
import com.fieldbook.tracker.brapi.Image;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ImageTest {

    private String filePath = "/path/to/file/file.jpg";
    private Image image;

    @Before
    public void setUp() throws Exception {
        this.image = new Image(filePath);
    }

    @Test
    public void correctWidthHeight() {
        assertTrue("Width check", image.getWidth() == 100);
        assertTrue("Height check", image.getHeight() == 100);
    }

    @Test
    public void correctFilename() {
        assertTrue("Filename check", image.getFileName().equals("file.jpg"));
    }

    @Test
    public void correctMimeType() {
        assertTrue("MimeType check", image.getMimeType().equals("image/jpeg"));
    }




}
