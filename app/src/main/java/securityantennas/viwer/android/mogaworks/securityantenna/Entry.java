package securityantennas.viwer.android.mogaworks.securityantenna;

/**
 * Created by mbkzmks on 2016/02/08.
 */
public class Entry {

    public final String id;
    public final String title;
    public final String link;
    public final long published;

    public Entry(String id, String title, String link, long published) {
        this.id = id;
        this.title = title;
        this.link = link;
        this.published = published;
    }
}
