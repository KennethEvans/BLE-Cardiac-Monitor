package net.kenevans.android.blecardiacmonitor;

public class GPXUtils {
    /**
     * Lines for the beginning of a GPX file with two %s for creator name and
     * creation time.
     */
    public static final String GPX_FILE_START_LINES = ""
            + "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" " +
            "creator=\"%s\" version=\"1.1\">\n"
            + "    <metadata>\n" + "        <time>%s</time>\n"
            + "    </metadata>\n" + "   <trk>\n" + "        <trkseg>\n";

    /**
     * Lines for a track point in a GPX file with two %s for time and HR.
     */
    public static final String GPX_FILE_TRACK_LINES = ""
            + "            <trkpt lat=\"0\" lon=\"0\">\n"
            + "                <ele>0</ele>\n"
            + "                <time>%s</time>\n"
            + "                <extensions>\n"
            + "                    <gpxtpx:TrackPointExtension " +
            "xmlns:gpxtpx=\"http://www.garmin" +
            ".com/xmlschemas/TrackPointExtension/v1\" " +
            "xmlns:gpxdata=\"http://www.cluetrust.com/XML/GPXDATA/1/0\" " +
            "xmlns:gpxx=\"http://www.garmin.com/xmlschemas/GpxExtensions/v3\"" +
            " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
            + "                        <gpxtpx:hr>%s</gpxtpx:hr>\n"
            + "                        <gpxdata:distance>0" +
            ".0</gpxdata:distance>\n"
            + "                    </gpxtpx:TrackPointExtension>\n"
            + "                </extensions>\n" + "            </trkpt>\n";

    /**
     * Lines for the end of a GPX file with no %s.
     */
    public static final String GPX_FILE_END_LINES = "        </trkseg>\n"
            + "    </trk>\n" + "</gpx>\n";

}
