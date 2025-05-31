import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestBooks {
    public static void main(String[] args) throws Exception {

        BookShop shop = MyXMLDataBinder.CreateObjectFromXMLfile(new File("input/books1.xml"), BookShop.class);
        System.out.println("Number of books: " + shop.book.size());

        for (int i = 0; i < shop.book.size(); i++) {
            Bookdata book = shop.book.get(i);

            System.out.println("Book #" + (i+1));
            System.out.println("Title: " + book.title);
            System.out.println("Price: " + book.price);
            System.out.println("Description: " + book.description);

            System.out.println("Authors:");
            for (Persondata author : book.author) {
                System.out.println("  - " + author.name + " " + author.surname + " (" + author.cv + ")");
            }
            System.out.println();
        }

        String xmlOutput = MyXMLDataBinder.CreateXMLFromObject(shop);

        Path outputDir = Paths.get("output");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        Files.write(outputDir.resolve("books_output.xml"), xmlOutput.getBytes());
        System.out.println("Marshalled object back to books_output.xml");
    }
}
