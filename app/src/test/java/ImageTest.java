import android.graphics.Bitmap;

import com.fieldbook.tracker.brapi.Image;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ImageTest {

    private String filePath = "/path/to/file/file.jpg";
    private Image image;

    @Before
    public void setUp() throws Exception {
        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        Bitmap bmp = Bitmap.createBitmap(100, 100, conf);
        image = new Image(filePath, bmp);
        image.loadImage();
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
    public void correctImagename() {
        assertTrue("Imagename check", image.getImageName().equals("file.jpg"));
    }

    @Test
    public void correctMimeType() {
        assertTrue("MimeType check", image.getMimeType().equals("image/jpeg"));
    }

}
