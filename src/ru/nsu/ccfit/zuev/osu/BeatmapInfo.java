package ru.nsu.ccfit.zuev.osu;

import com.rian.osu.beatmap.Beatmap;
import ru.nsu.ccfit.zuev.osu.helper.StringTable;
import ru.nsu.ccfit.zuev.osuplus.R;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;

public class BeatmapInfo implements Serializable {
    private static final long serialVersionUID = -3865268984942011628L;
    private final ArrayList<TrackInfo> tracks = new ArrayList<>();
    private String title;
    private String titleUnicode;
    private String artist;
    private String artistUnicode;
    private String creator;
    private String path;
    private String source;
    private String tags;
    private String music = null;
    private long date;
    private int previewTime;

    public String getSource() {
        return source;
    }

    public void setSource(final String source) {
        this.source = source;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(final String tags) {
        this.tags = tags;
    }

    public String getMusic() {
        return music;
    }

    public void setMusic(final String music) {
        this.music = music;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(final String artist) {
        this.artist = artist;
    }

    public String getTitleUnicode() {
        return titleUnicode;
    }

    public void setTitleUnicode(String titleUnicode) {
        this.titleUnicode = titleUnicode;
    }

    public String getArtistUnicode() {
        return artistUnicode;
    }

    public void setArtistUnicode(String artistUnicode) {
        this.artistUnicode = artistUnicode;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(final String creator) {
        this.creator = creator;
    }

    public String getPath() {
        return path;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    public void addTrack(final TrackInfo track) {
        tracks.add(track);
    }

    public TrackInfo getTrack(final int index) {
        return tracks.get(index);
    }

    public int getCount() {
        return tracks.size();
    }

    public ArrayList<TrackInfo> getTracks() {
        return tracks;
    }

    public long getDate() {
        return date;
    }

    public void setDate(final long date) {
        this.date = date;
    }

    public int getPreviewTime() {
        return previewTime;
    }

    public void setPreviewTime(final int previewTime) {
        this.previewTime = previewTime;
    }

    /**
     * Given a <code>Beatmap</code>, populate the metadata of this <code>BeatmapInfo</code>
     * with that <code>Beatmap</code>.
     *
     * @param beatmap The <code>Beatmap</code> to populate.
     * @return Whether this <code>BeatmapInfo</code> was successfully populated.
     */
    public boolean populate(Beatmap beatmap) {
        // General
        if (music == null) {
            final File musicFile = new File(path, beatmap.getGeneral().audioFilename);

            if (!musicFile.exists()) {
                ToastLogger.showText(StringTable.format(R.string.beatmap_parser_music_not_found,
                        beatmap.filename.substring(0, Math.max(0, beatmap.filename.length() - 4))), true);
                return false;
            }

            music = musicFile.getPath();
            previewTime = beatmap.getGeneral().previewTime;
        }

        // Metadata
        if (title == null) {
            title = beatmap.getMetadata().title;
        }

        if (titleUnicode == null) {
            String titleUnicode = beatmap.getMetadata().titleUnicode;
            if (!titleUnicode.isEmpty()) {
                this.titleUnicode = titleUnicode;
            }
        }

        if (artist == null) {
            artist = beatmap.getMetadata().artist;
        }

        if (artistUnicode == null) {
            String artistUnicode = beatmap.getMetadata().artist;
            if (!artistUnicode.isEmpty()) {
                this.artistUnicode = artistUnicode;
            }
        }

        if (source == null) {
            source = beatmap.getMetadata().source;
        }

        if (tags == null) {
            tags = beatmap.getMetadata().tags;
        }

        return true;
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof BeatmapInfo && ((BeatmapInfo) o).getPath().equals(path);
    }
}
