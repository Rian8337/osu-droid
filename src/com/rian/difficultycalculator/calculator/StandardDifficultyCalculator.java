package com.rian.difficultycalculator.calculator;

import com.rian.difficultycalculator.attributes.StandardDifficultyAttributes;
import com.rian.difficultycalculator.beatmap.BeatmapDifficultyManager;
import com.rian.difficultycalculator.beatmap.DifficultyBeatmap;
import com.rian.difficultycalculator.beatmap.hitobject.DifficultyHitObject;
import com.rian.difficultycalculator.skills.Skill;
import com.rian.difficultycalculator.skills.standard.StandardAim;
import com.rian.difficultycalculator.skills.standard.StandardFlashlight;
import com.rian.difficultycalculator.skills.standard.StandardSpeed;
import com.rian.difficultycalculator.utils.GameMode;
import com.rian.difficultycalculator.utils.HitObjectStackEvaluator;
import com.rian.difficultycalculator.utils.StandardHitWindowConverter;

import java.util.EnumSet;
import java.util.List;

import main.osu.game.mods.GameMod;

/**
 * A difficulty calculator for osu!standard.
 */
public class StandardDifficultyCalculator extends DifficultyCalculator {
    private static final double difficultyMultiplier = 0.0675;

    public StandardDifficultyCalculator() {
        super(GameMode.standard);
    }

    @Override
    protected StandardDifficultyAttributes createDifficultyAttributes(final DifficultyBeatmap beatmap, final Skill[] skills,
                                                                      final List<DifficultyHitObject> objects,
                                                                      final DifficultyCalculationParameters parameters) {
        StandardDifficultyAttributes attributes = new StandardDifficultyAttributes();

        attributes.aimDifficulty = calculateRating(skills[0]);
        attributes.speedDifficulty = calculateRating(skills[2]);
        attributes.speedNoteCount = ((StandardSpeed) skills[2]).relevantNoteCount();
        attributes.flashlightDifficulty = calculateRating(skills[3]);

        double aimRatingNoSliders = calculateRating(skills[1]);
        attributes.aimSliderFactor = attributes.aimDifficulty > 0 ? aimRatingNoSliders / attributes.aimDifficulty : 1;

        if (parameters != null && parameters.mods.contains(GameMod.MOD_RELAX)) {
            attributes.aimDifficulty *= 0.9;
            attributes.speedDifficulty = 0;
            attributes.flashlightDifficulty *= 0.7;
        }

        double baseAimPerformance = Math.pow(5 * Math.max(1, attributes.aimDifficulty / 0.0675) - 4, 3) / 100000;
        double baseSpeedPerformance = Math.pow(5 * Math.max(1, attributes.speedDifficulty / 0.0675) - 4, 3) / 100000;
        double baseFlashlightPerformance = 0;

        if (parameters != null && parameters.mods.contains(GameMod.MOD_FLASHLIGHT)) {
            baseFlashlightPerformance = Math.pow(attributes.flashlightDifficulty, 2) * 25.0;
        }

        double basePerformance = Math.pow(
                Math.pow(baseAimPerformance, 1.1) +
                        Math.pow(baseSpeedPerformance, 1.1) +
                        Math.pow(baseFlashlightPerformance, 1.1),
                1.0 / 1.1
        );

        // Document for formula derivation:
        // https://docs.google.com/document/d/10DZGYYSsT_yjz2Mtp6yIJld0Rqx4E-vVHupCqiM4TNI/edit
        attributes.starRating = basePerformance > 1e-5
                ? Math.cbrt(StandardPerformanceCalculator.finalMultiplier) * 0.027 * (Math.cbrt(100000 / Math.pow(2, 1 / 1.1) * basePerformance) + 4)
                : 0;

        float ar = beatmap.getDifficultyManager().getAR();
        float preempt = (ar <= 5) ? (1800 - 120 * ar) : (1950 - 150 * ar);

        if (parameters != null && !parameters.isForceAR()) {
            preempt /= parameters.getTotalSpeedMultiplier();
        }

        attributes.approachRate = preempt > 1200 ? (1800 - preempt) / 120 : (1200 - preempt) / 150 + 5;

        float od = beatmap.getDifficultyManager().getOD();
        float odMS = StandardHitWindowConverter.odToHitWindow300(od) / (parameters != null ? parameters.getTotalSpeedMultiplier() : 1f);

        attributes.overallDifficulty = StandardHitWindowConverter.hitWindow300ToOD(odMS);

        attributes.maxCombo = beatmap.getMaxCombo();
        attributes.hitCircleCount = beatmap.getHitObjectsManager().getCircleCount();
        attributes.sliderCount = beatmap.getHitObjectsManager().getSliderCount();
        attributes.spinnerCount = beatmap.getHitObjectsManager().getSpinnerCount();

        return attributes;
    }

    @Override
    protected void applyParameters(DifficultyBeatmap beatmap, DifficultyCalculationParameters parameters) {
        final BeatmapDifficultyManager manager = beatmap.getDifficultyManager();

        processCS(manager, parameters);
        processAR(manager, parameters);
        processOD(manager, parameters);
        processHP(manager, parameters);

        HitObjectStackEvaluator.applyStandardStacking(
                beatmap.getFormatVersion(),
                beatmap.getHitObjectsManager().getObjects(),
                manager.getAR(),
                beatmap.getStackLeniency()
        );
    }

    @Override
    protected Skill[] createSkills(DifficultyBeatmap beatmap, DifficultyCalculationParameters parameters) {
        EnumSet<GameMod> mods = EnumSet.noneOf(GameMod.class);
        float od = beatmap.getDifficultyManager().getOD();
        float greatWindow = StandardHitWindowConverter.odToHitWindow300(od);

        if (parameters != null) {
            mods = parameters.mods;
            greatWindow /= parameters.getTotalSpeedMultiplier();
        }

        return new Skill[] {
                new StandardAim(mods, true),
                new StandardAim(mods, false),
                new StandardSpeed(mods, greatWindow),
                new StandardFlashlight(mods),
        };
    }

    private double calculateRating(Skill skill) {
        return Math.sqrt(skill.difficultyValue()) * difficultyMultiplier;
    }

    private void processCS(BeatmapDifficultyManager manager, DifficultyCalculationParameters parameters) {
        float cs = manager.getCS();

        if (parameters != null) {
            if (parameters.mods.contains(GameMod.MOD_HARDROCK)) {
                ++cs;
            }
            if (parameters.mods.contains(GameMod.MOD_EASY)) {
                --cs;
            }
            if (parameters.mods.contains(GameMod.MOD_REALLYEASY)) {
                --cs;
            }
            if (parameters.mods.contains(GameMod.MOD_SMALLCIRCLE)) {
                cs += 4f;
            }
        }

        // 12.14 is the point at which the object radius approaches 0. Use the _very_ minimum value.
        manager.setCS(Math.min(cs, 12.13f));
    }

    private void processAR(BeatmapDifficultyManager manager, DifficultyCalculationParameters parameters) {
        float ar = manager.getAR();

        if (parameters == null) {
            manager.setAR(Math.min(ar, 10));
            return;
        }

        if (parameters.isForceAR()) {
            manager.setAR(parameters.forcedAR);
        } else {
            if (parameters.mods.contains(GameMod.MOD_HARDROCK)) {
                ar *= 1.4f;
            }
            if (parameters.mods.contains(GameMod.MOD_EASY)) {
                ar /= 2f;
            }
            if (parameters.mods.contains(GameMod.MOD_REALLYEASY)) {
                if (parameters.mods.contains(GameMod.MOD_EASY)) {
                    ar *= 2f;
                    ar -= 0.5f;
                }

                ar -= 0.5f;
                ar -= parameters.customSpeedMultiplier - 1f;
            }

            manager.setAR(Math.min(ar, 10f));
        }
    }

    private void processOD(BeatmapDifficultyManager manager, DifficultyCalculationParameters parameters) {
        float od = manager.getOD();

        if (parameters != null) {
            if (parameters.mods.contains(GameMod.MOD_HARDROCK)) {
                od *= 1.4f;
            }
            if (parameters.mods.contains(GameMod.MOD_EASY)) {
                od /= 2f;
            }
            if (parameters.mods.contains(GameMod.MOD_REALLYEASY)) {
                od /= 2f;
            }
        }

        manager.setOD(Math.min(od, 10f));
    }

    private void processHP(BeatmapDifficultyManager manager, DifficultyCalculationParameters parameters) {
        float hp = manager.getHP();

        if (parameters != null) {
            if (parameters.mods.contains(GameMod.MOD_HARDROCK)) {
                hp *= 1.4f;
            }
            if (parameters.mods.contains(GameMod.MOD_EASY)) {
                hp /= 2f;
            }
            if (parameters.mods.contains(GameMod.MOD_REALLYEASY)) {
                hp /= 2f;
            }
        }

        manager.setHP(Math.min(hp, 10));
    }
}
