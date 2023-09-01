package com.rian.osu.beatmap.parser

import android.util.Log
import com.reco1l.framework.extensions.ignoreException
import com.rian.osu.beatmap.Beatmap
import com.rian.osu.beatmap.constants.BeatmapSection
import com.rian.osu.beatmap.parser.sections.*
import com.rian.osu.utils.HitObjectStackEvaluator.applyStacking
import okio.BufferedSource
import okio.buffer
import okio.source
import ru.nsu.ccfit.zuev.osu.ToastLogger
import ru.nsu.ccfit.zuev.osu.helper.FileUtils
import ru.nsu.ccfit.zuev.osu.helper.StringTable
import ru.nsu.ccfit.zuev.osuplus.R
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.regex.Pattern

/**
 * A parser for parsing `.osu` files.
 */
class BeatmapParser : Closeable {
    /**
     * The `.osu` file.
     */
    private val file: File

    /**
     * The `BufferedSource` responsible for reading the beatmap file's contents.
     */
    private var source: BufferedSource? = null

    /**
     * The format version of the beatmap.
     */
    private var beatmapFormatVersion = 14

    /**
     * @param file The `.osu` file.
     */
    constructor(file: File) {
        this.file = file
    }

    /**
     * @param path The path to the `.osu` file.
     */
    constructor(path: String) {
        file = File(path)
    }

    /**
     * Attempts to open the beatmap file.
     *
     * @return Whether the beatmap file was successfully opened.
     */
    fun openFile(): Boolean {
        try {
            source = file.source().buffer()
        } catch (e: IOException) {
            Log.e("BeatmapParser.openFile", e.message!!)
            source = null
            return false
        }

        try {
            val head = source!!.readUtf8Line() ?: return false

            val pattern = Pattern.compile("osu file format v(\\d+)")
            val matcher = pattern.matcher(head)

            if (!matcher.find()) {
                return false
            }

            val formatPos = head.indexOf("file format v")

            beatmapFormatVersion = head.substring(formatPos + 13).toIntOrNull() ?: beatmapFormatVersion
        } catch (e: Exception) {
            Log.e("BeatmapParser.openFile", e.message!!)
        }

        return true
    }

    /**
     * Parses the `.osu` file.
     *
     * @param withHitObjects Whether to parse hit objects. This will improve parsing time significantly.
     * @return A `Beatmap` containing relevant information of the beatmap file,
     * `null` if the beatmap file cannot be opened or a line could not be parsed.
     */
    fun parse(withHitObjects: Boolean): Beatmap? {
        if (source == null && !openFile()) {
            ToastLogger.showText(
                StringTable.format(R.string.beatmap_parser_cannot_open_file, file.nameWithoutExtension),
                true
            )

            return null
        }

        var currentLine: String?
        var currentSection: BeatmapSection? = null
        val beatmap = Beatmap().apply {
            md5 = FileUtils.getMD5Checksum(file)
            folder = file.getParent()
            filename = file.path
            formatVersion = beatmapFormatVersion
        }

        try {
            while (source!!.readUtf8Line().also { currentLine = it } != null) {
                // Check if beatmap is not an osu!standard beatmap
                if (beatmap.general.mode != 0) {
                    // Silently ignore (do not log anything to the user)
                    return null
                }

                var line = currentLine ?: continue

                // Handle space comments
                if (line.startsWith(" ") || line.startsWith("_")) {
                    continue
                }

                // Now that we've handled space comments, we can trim space
                line = line.trim { it <= ' ' }

                // Handle C++ style comments and empty lines
                if (line.startsWith("//") || line.isEmpty()) {
                    continue
                }

                // [SectionName]
                if (line.startsWith("[") && line.endsWith("]")) {
                    currentSection = BeatmapSection.parse(line.substring(1, line.length - 1))
                    continue
                }

                if (currentSection == null) {
                    continue
                }

                try {
                    when (currentSection) {
                        BeatmapSection.General ->
                            BeatmapGeneralParser.parse(beatmap, line)

                        BeatmapSection.Metadata ->
                            BeatmapMetadataParser.parse(beatmap, line)

                        BeatmapSection.Difficulty ->
                            BeatmapDifficultyParser.parse(beatmap, line)

                        BeatmapSection.Events ->
                            BeatmapEventsParser.parse(beatmap, line)

                        BeatmapSection.TimingPoints ->
                            BeatmapControlPointsParser.parse(beatmap, line)

                        BeatmapSection.Colors ->
                            BeatmapColorParser.parse(beatmap, line)

                        BeatmapSection.HitObjects ->
                            if (withHitObjects) {
                                BeatmapHitObjectsParser.parse(beatmap, line)
                            }

                        else -> continue
                    }
                } catch (e: Exception) {
                    Log.e("BeatmapParser.parse", "Unable to parse line $line", e)
                }
            }

            populateObjectData(beatmap)
        } catch (e: IOException) {
            Log.e("BeatmapParser.parse", e.message!!)
            return null
        }

        return beatmap
    }

    private fun checkExtension() {
        if (file.extension.lowercase() != "osu") {
            throw UnsupportedOperationException("Beatmap parser can only parse .osu files.")
        }
    }

    override fun close() {
        ignoreException { source?.close() }

        source = null
    }

    companion object {
        /**
         * Populates the object scales of a `Beatmap`.
         *
         * @param beatmap The `Beatmap` whose object scales will be populated.
         */
        fun populateObjectData(beatmap: Beatmap) {
            val scale = (1 - 0.7f * (beatmap.difficulty.cs - 5) / 5) / 2

            beatmap.hitObjects.apply {
                resetStacking()

                getObjects().apply {
                    forEach { it.scale = scale }

                    applyStacking(
                        beatmap.formatVersion,
                        this,
                        beatmap.difficulty.ar,
                        beatmap.general.stackLeniency
                    )
                }
            }
        }
    }
}
