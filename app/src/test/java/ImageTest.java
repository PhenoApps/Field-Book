import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;

import com.fieldbook.tracker.brapi.model.FieldBookImage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class ImageTest {

    private String filePath = "/path/to/file/file.jpg";
    private FieldBookImage image;

    @Before
    public void setUp() throws Exception {
        Context context = mock(Context.class);
        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        Bitmap bmp = Bitmap.createBitmap(100, 100, conf);
        image = new FieldBookImage(context, filePath, "trait1", bmp);
        image.loadImage(context);
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
