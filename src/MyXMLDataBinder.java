import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

public class MyXMLDataBinder {

    // Unmarshal XML into an object
    public static <T> T CreateObjectFromXMLfile(File xmlFile, Class<T> rootClass) throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        Element root = doc.getDocumentElement();
        return readElementToObject(root, rootClass);
    }

    // Marshal an object into XML string
    public static String CreateXMLFromObject(Object obj) throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.newDocument();

        Element root = createElementFromObject(doc, obj.getClass().getSimpleName().toLowerCase(), obj);
        doc.appendChild(root);

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        StringWriter writer = new StringWriter();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    private static <T> T readElementToObject(Element element, Class<T> clazz) throws Exception {
        T obj = clazz.getDeclaredConstructor().newInstance();

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element) {
                Element childElem = (Element) n;
                String name = childElem.getTagName();
                Field field;
                try {
                    field = clazz.getField(name);
                } catch (NoSuchFieldException e) {
                    continue;
                }

                Class<?> fieldType = field.getType();

                if (List.class.isAssignableFrom(fieldType)) {
                    ParameterizedType pt = (ParameterizedType) field.getGenericType();
                    Class<?> itemType = (Class<?>) pt.getActualTypeArguments()[0];

                    List<Object> list = (List<Object>) field.get(obj);
                    if (list == null) {
                        list = new ArrayList<>();
                        field.set(obj, list);
                    }

                    list.add(readElementToObject(childElem, itemType));

                } else if (isPrimitiveOrWrapper(fieldType)) {
                    Object value = parsePrimitive(fieldType, childElem.getTextContent());
                    field.set(obj, value);

                } else {
                    field.set(obj, readElementToObject(childElem, fieldType));
                }
            }
        }

        return obj;
    }

    private static Element createElementFromObject(Document doc, String tagName, Object obj) throws Exception {
        Element element = doc.createElement(tagName);

        for (Field field : obj.getClass().getFields()) {
            Object value = field.get(obj);
            if (value == null) continue;

            if (value instanceof List<?>) {
                List<?> list = (List<?>) value;
                for (Object item : list) {
                    Element child = createElementFromObject(doc, item.getClass().getSimpleName().toLowerCase(), item);
                    element.appendChild(child);
                }
            } else if (isPrimitiveOrWrapper(field.getType())) {
                Element child = doc.createElement(field.getName());
                child.setTextContent(value.toString());
                element.appendChild(child);
            } else {
                Element child = createElementFromObject(doc, field.getName(), value);
                element.appendChild(child);
            }
        }

        return element;
    }

    private static boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive()
                || type == String.class
                || type == Integer.class || type == int.class
                || type == Long.class || type == long.class
                || type == Float.class || type == float.class
                || type == Double.class || type == double.class
                || type == Boolean.class || type == boolean.class;
    }

    private static Object parsePrimitive(Class<?> type, String value) {
        if (type == int.class || type == Integer.class)
            return Integer.parseInt(value);
        if (type == long.class || type == Long.class)
            return Long.parseLong(value);
        if (type == float.class || type == Float.class)
            return Float.parseFloat(value);
        if (type == double.class || type == Double.class)
            return Double.parseDouble(value);
        if (type == boolean.class || type == Boolean.class)
            return Boolean.parseBoolean(value);
        return value;
    }
}
