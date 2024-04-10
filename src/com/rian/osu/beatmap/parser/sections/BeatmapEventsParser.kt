package com.rian.osu.beatmap.parser.sections

import com.rian.osu.beatmap.Beatmap
import com.rian.osu.beatmap.timings.BreakPeriod
import ru.nsu.ccfit.zuev.osu.RGBColor
import kotlin.math.max

/**
 * A parser for parsing a beatmap's events section.
 */
object BeatmapEventsParser : BeatmapSectionParser() {
    override fun parse(beatmap: Beatmap, line: String) = line
        .split("\\s*,\\s*".toRegex())
        .dropLastWhile { it.isEmpty() }
        .let {
            if (it.size >= 3) {
                if (line.startsWith("0,0")) {
                    beatmap.events.backgroundFilename = it[2].substring(1, it[2].length - 1)
                }

                if (line.startsWith("2") || line.startsWith("Break")) {
                    val start = beatmap.getOffsetTime(parseInt(it[1]))
                    val end = max(start, beatmap.getOffsetTime(parseInt(it[2])))

                    beatmap.events.breaks.add(BreakPeriod(start.toFloat(), end.toFloat()))
                }

                if (line.startsWith("1") || line.startsWith("Video")) {
                    beatmap.events.videoStartTime = parseInt(it[1])
                    beatmap.events.videoFilename = it[2].substring(1, it[2].length - 1)
                }
            }
    
            if (it.size >= 5 && line.startsWith("3")) {
                beatmap.events.backgroundColor = RGBColor(
                    parseInt(it[2]).toFloat(),
                    parseInt(it[3]).toFloat(),
                    parseInt(it[4]).toFloat()
                )
            }
        }
}
