import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestDots {
    public static void main(String[] args) throws Exception {

        Dots dots = MyXMLDataBinder.CreateObjectFromXMLfile(new File("input/dots1.xml"), Dots.class);
        System.out.println("Number of dots: " + dots.dot.size() + "\n");

        int index = 1;
        for (Dot dot : dots.dot) {
            System.out.println("Dot #" + index++);
            System.out.println("  x: " + dot.x);
            System.out.println("  y: " + dot.y);
            System.out.println();
        }

        String xmlOutput = MyXMLDataBinder.CreateXMLFromObject(dots);

        Path outputDir = Paths.get("output");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        Files.write(outputDir.resolve("dots_output1.xml"), xmlOutput.getBytes());
        System.out.println("Marshalled object back to dots_output1.xml");
    }
}
