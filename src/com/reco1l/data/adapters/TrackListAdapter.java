package com.reco1l.data.adapters;

// Created by Reco1l on 18/9/22 00:08

import static com.reco1l.data.adapters.TrackListAdapter.*;

import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.caverock.androidsvg.SVGImageView;
import com.reco1l.Game;
import com.reco1l.ui.UI;
import com.reco1l.data.BaseAdapter;
import com.reco1l.data.BaseViewHolder;
import com.reco1l.ui.scenes.Scenes;
import com.reco1l.framework.Animation;
import com.reco1l.tools.Views;
import com.reco1l.tools.helpers.BeatmapHelper;
import com.reco1l.view.BadgeTextView;
import com.reco1l.view.RoundLayout;
import com.reco1l.view.effects.StripsEffect;

import java.util.ArrayList;

import main.osu.TrackInfo;
import main.osu.game.GameHelper;
import com.rimu.R;

public class TrackListAdapter extends BaseAdapter<TrackViewHolder, TrackInfo> {

    //--------------------------------------------------------------------------------------------//

    public TrackListAdapter(ArrayList<TrackInfo> items) {
        super(items);
    }

    //--------------------------------------------------------------------------------------------//

    @Override
    protected int getItemLayout() {
        return R.layout.item_beatmap_child;
    }

    @Override
    protected TrackViewHolder getViewHolder(View rootView) {
        return new TrackViewHolder(rootView);
    }

    //--------------------------------------------------------------------------------------------//

    public static class TrackViewHolder extends BaseViewHolder<TrackInfo> {

        private final RoundLayout mBody;

        private final SVGImageView mMarkImage;
        private final TextView mDifficultyText;
        private final BadgeTextView mStarsText;

        private final StripsEffect mEffects;

        //----------------------------------------------------------------------------------------//

        public TrackViewHolder(@NonNull View root) {
            super(root);

            mEffects = new StripsEffect(context());
            mBody = root.findViewById(R.id.bl_itemChildBody);

            mMarkImage = root.findViewById(R.id.bl_mark);
            mStarsText = root.findViewById(R.id.bl_stars);
            mDifficultyText = root.findViewById(R.id.bl_difficulty);

            UI.beatmapCarrousel.bindTouch(mBody, () -> {
                if(!select()) {
                    Game.resourcesManager.getSound("menuhit").play();
                    Scenes.player.startGame(item, null);
                } else {
                    Scenes.selector.onTrackSelect(item);
                }
            });
        }

        //----------------------------------------------------------------------------------------//

        @Override
        protected void onBind(TrackInfo item, int position) {
            mDifficultyText.setText(item.getMode());
            mStarsText.setText("" + GameHelper.Round(item.getDifficulty(), 2));

            float sr = item.getDifficulty();

            int color = BeatmapHelper.Palette.getColor(sr);
            int textColor = BeatmapHelper.Palette.getTextColor(sr);

            String mark = Game.scoreLibrary.getBestMark(item.getFilename());

            if (mark != null) {
                mMarkImage.setImageAsset("svg/ranking-" + mark + ".svg");
                mMarkImage.setVisibility(View.VISIBLE);
            }

            mStarsText.setTextColor(textColor);
            mStarsText.setBackground(new ColorDrawable(color));
            mEffects.setStripColor(color);
        }

        //----------------------------------------------------------------------------------------//

        @Override
        public void onSelect() {
            Animation.of(mBody)
                    .toLeftMargin(0)
                    .play(300);

            mBody.addView(mEffects, 0, Views.match_parent);
        }

        @Override
        public void onDeselect() {
            Animation.of(mBody)
                    .toLeftMargin(sdp(18))
                    .play(300);

            mBody.removeView(mEffects);
        }
    }
}
