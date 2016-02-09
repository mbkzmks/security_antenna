/*
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package securityantennas.viwer.android.mogaworks.securityantenna.net;

import android.text.format.Time;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import securityantennas.viwer.android.mogaworks.securityantenna.Entry;

/**
 * This class parses generic Atom feeds.
 *
 * <p>Given an InputStream representation of a feed, it returns a List of entries,
 * where each list element represents a single entry (post) in the XML feed.
 *
 * <p>An example of an Atom feed can be found at:
 * http://en.wikipedia.org/w/index.php?title=Atom_(standard)&oldid=560239173#Example_of_an_Atom_1.0_feed
 */
public class FeedParser {
    public static final String TAG = "FeedParser";
    // Constants indicting XML element names that we're interested in
    private static final int TAG_ID = 1;
    private static final int TAG_TITLE = 2;
    private static final int TAG_PUBLISHED = 3;
    private static final int TAG_LINK = 4;
    private int position = 0;
    // We don't use XML namespaces
    private static final String ns = null;
    public FeedParser(int position){
        this.position  = position;
    }
    /** Parse an Atom feed, returning a collection of Entry objects.
     *
     * @param in Atom feed, as a stream.
     * @return List of {@link FeedParser.Entry} objects.
     * @throws XmlPullParserException on error parsing feed.
     * @throws IOException on I/O error.
     */
    public List<Entry> parse(InputStream in)
            throws XmlPullParserException, IOException, ParseException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();
            return readFeed(parser);
        } finally {
            in.close();
        }
    }

    /**
     * Decode a feed attached to an XmlPullParser.
     *
     * @param parser Incoming XMl
     * @return List of {@link FeedParser.Entry} objects.
     * @throws XmlPullParserException on error parsing feed.
     * @throws IOException on I/O error.
     */
    private List<Entry> readFeed(XmlPullParser parser)
            throws XmlPullParserException, IOException, ParseException {
        List<Entry> entries = new ArrayList<Entry>();


        // Search for <feed> tags. These wrap the beginning/end of an Atom document.
        //
        // Example:
        // <?xml version="1.0" encoding="utf-8"?>
        // <feed xmlns="http://www.w3.org/2005/Atom">
        // ...
        // </feed>
        parser.require(XmlPullParser.START_TAG, ns, "rdf:RDF");
        Log.d(TAG, "readFeed:" + parser.getName());
        Log.d(TAG, "readFeed:" + parser.getNamespace());

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            Log.d(TAG, "name:" + name);
            // Starts by looking for the <entry> tag. This tag repeates inside of <feed> for each
            // article in the feed.
            //
            // Example:
            // <entry>
            //   <title>Article title</title>
            //   <link rel="alternate" type="text/html" href="http://example.com/article/1234"/>
            //   <link rel="edit" href="http://example.com/admin/article/1234"/>
            //   <id>urn:uuid:218AC159-7F68-4CC6-873F-22AE6017390D</id>
            //   <published>2003-06-27T12:00:00Z</published>
            //   <updated>2003-06-28T12:00:00Z</updated>
            //   <summary>Article summary goes here.</summary>
            //   <author>
            //     <name>Rick Deckard</name>
            //     <email>deckard@example.com</email>
            //   </author>
            // </entry>
            if (name.equals("item")) {
                entries.add(readEntry(parser));
            } else {
                skip(parser);
            }
        }
        return entries;
    }

    /**
     * Parses the contents of an entry. If it encounters a title, summary, or link tag, hands them
     * off to their respective "read" methods for processing. Otherwise, skips the tag.
     */
    private Entry readEntry(XmlPullParser parser)
            throws XmlPullParserException, IOException, ParseException {
        parser.require(XmlPullParser.START_TAG, ns, "item");
        String id = null;
        String title = null;
        String link = null;
        long publishedOn = 0;

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            Log.d(TAG, "item_name:" + name);
            if (name.equals("dc:identifier")){
                // Example: <id>urn:uuid:218AC159-7F68-4CC6-873F-22AE6017390D</id>
                id = readTag(parser, TAG_ID);
            } else if (name.equals("title")) {
                // Example: <title>Article title</title>
                title = readTag(parser, TAG_TITLE);
            } else if (name.equals("link")) {
                link = readTag(parser, TAG_LINK);
            } else if (name.equals("dc:date")) {
                // Example: <published>2003-06-27T12:00:00Z</published>
                Time t = new Time();
                String dcdate = readTag(parser, TAG_PUBLISHED);
                Log.d(TAG, dcdate);
                if(position ==1){
                    id = dcdate;
                    publishedOn = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZZZZZ").parse(dcdate).getTime();
                }else{
                    t.parse3339(dcdate);
                    publishedOn = t.toMillis(false);
                    if(position ==0) id = dcdate;
                }

            } else {
                skip(parser);
            }
        }
        return new Entry(id, title, link, publishedOn);
    }

    /**
     * Process an incoming tag and read the selected value from it.
     */
    private String readTag(XmlPullParser parser, int tagType)
            throws IOException, XmlPullParserException {
        String tag = null;
        String endTag = null;

        switch (tagType) {
            case TAG_ID:
                return readBasicTag(parser, "dc:identifier");
            case TAG_TITLE:
                return readBasicTag(parser, "title");
            case TAG_PUBLISHED:
                return readBasicTag(parser, "dc:date");
            case TAG_LINK:
                return readBasicTag(parser, "link");
            default:
                throw new IllegalArgumentException("Unknown tag type: " + tagType);
        }
    }

    /**
     * Reads the body of a basic XML tag, which is guaranteed not to contain any nested elements.
     *
     * <p>You probably want to call readTag().
     *
     * @param parser Current parser object
     * @param tag XML element tag name to parse
     * @return Body of the specified tag
     * @throws IOException
     * @throws XmlPullParserException
     */
    private String readBasicTag(XmlPullParser parser, String tag)
            throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, tag);
        String result = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, tag);
        return result;
    }

    /**
     * Processes link tags in the feed.
     * 削除候補
     */
    private String readAlternateLink(XmlPullParser parser)
            throws IOException, XmlPullParserException {
        String link = null;
        parser.require(XmlPullParser.START_TAG, ns, "link");
        String tag = parser.getName();
        Log.d("aaaaaaa", tag);
        String relType = parser.getAttributeValue(null, "rel");
        if (relType.equals("alternate")) {
            link = parser.getAttributeValue(null, "href");
        }
        while (true) {
            if (parser.nextTag() == XmlPullParser.END_TAG) break;
            // Intentionally break; consumes any remaining sub-tags.
        }
        return link;
    }
    /**
     * For the tags title and summary, extracts their text values.
     */
    private String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String result = null;
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

    /**
     * Skips tags the parser isn't interested in. Uses depth to handle nested tags. i.e.,
     * if the next tag after a START_TAG isn't a matching END_TAG, it keeps going until it
     * finds the matching END_TAG (as indicated by the value of "depth" being 0).
     */
    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }


}
