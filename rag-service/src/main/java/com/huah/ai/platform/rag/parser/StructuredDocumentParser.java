package com.huah.ai.platform.rag.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class StructuredDocumentParser {

    private final ObjectMapper objectMapper;

    public List<Document> parseCsv(Resource resource) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> lines = reader.lines().toList();
            if (lines.isEmpty()) {
                return List.of();
            }

            List<String> headers = parseCsvLine(lines.get(0));
            StringBuilder content = new StringBuilder("CSV Document\n\n");
            for (int i = 1; i < lines.size(); i++) {
                List<String> values = parseCsvLine(lines.get(i));
                if (values.stream().allMatch(String::isBlank)) {
                    continue;
                }
                content.append("Row ").append(i).append(": ");
                for (int j = 0; j < Math.max(headers.size(), values.size()); j++) {
                    String header = j < headers.size() && !headers.get(j).isBlank() ? headers.get(j) : "column_" + (j + 1);
                    String value = j < values.size() ? values.get(j) : "";
                    if (!value.isBlank()) {
                        content.append(header).append("=").append(value).append("; ");
                    }
                }
                content.append("\n");
            }

            return List.of(new Document(content.toString().trim(), Map.of(
                    "structured_type", "csv",
                    "row_count", Math.max(lines.size() - 1, 0),
                    "column_count", headers.size()
            )));
        } catch (IOException e) {
            throw new IllegalStateException("CSV parsing failed", e);
        }
    }

    public List<Document> parseJson(Resource resource) {
        try {
            Object value = objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {});
            List<Document> documents = new ArrayList<>();
            flattenJsonNode("root", value, documents);
            if (!documents.isEmpty()) {
                return documents;
            }
            return List.of(new Document("JSON Document\n\n(empty object)", Map.of("structured_type", "json")));
        } catch (IOException e) {
            throw new IllegalStateException("JSON parsing failed", e);
        }
    }

    public List<Document> parseXml(Resource resource) {
        try {
            String xml = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            org.w3c.dom.Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            Element root = document.getDocumentElement();
            if (root == null) {
                return List.of();
            }

            StringBuilder content = new StringBuilder("XML Document\n\n");
            appendXmlNode(root, content, 0);
            return List.of(new Document(content.toString().trim(), Map.of(
                    "structured_type", "xml",
                    "root_element", root.getTagName()
            )));
        } catch (Exception e) {
            throw new IllegalStateException("XML parsing failed", e);
        }
    }

    public List<Document> parseChemical(Resource resource) {
        throw new UnsupportedOperationException("Chemical structure parsing is not implemented yet.");
    }

    public List<Document> parseLabReport(Resource resource) {
        throw new UnsupportedOperationException("Lab report parsing is not implemented yet.");
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
                continue;
            }
            if (ch == ',' && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        values.add(current.toString().trim());
        return values;
    }

    private void flattenJsonNode(String path, Object value, List<Document> documents) {
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                documents.add(new Document(path + " = {}", Map.of("structured_type", "json", "json_path", path)));
                return;
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                flattenJsonNode(path + "." + entry.getKey(), entry.getValue(), documents);
            }
            return;
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                documents.add(new Document(path + " = []", Map.of("structured_type", "json", "json_path", path)));
                return;
            }
            for (int i = 0; i < list.size(); i++) {
                flattenJsonNode(path + "[" + i + "]", list.get(i), documents);
            }
            return;
        }

        String renderedValue = value == null ? "null" : String.valueOf(value);
        documents.add(new Document(path + " = " + renderedValue, Map.of(
                "structured_type", "json",
                "json_path", path
        )));
    }

    private void appendXmlNode(Node node, StringBuilder content, int depth) {
        String indent = "  ".repeat(Math.max(depth, 0));
        content.append(indent).append("<").append(node.getNodeName());
        NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attr = attributes.item(i);
                content.append(" ").append(attr.getNodeName()).append("=\"").append(attr.getNodeValue()).append("\"");
            }
        }
        content.append(">");

        NodeList children = node.getChildNodes();
        List<Node> elementChildren = new ArrayList<>();
        String textValue = "";
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                elementChildren.add(child);
            } else if (child.getNodeType() == Node.TEXT_NODE) {
                textValue = child.getTextContent() == null ? "" : child.getTextContent().trim();
            }
        }

        if (!textValue.isBlank()) {
            content.append(textValue);
        }
        if (!elementChildren.isEmpty()) {
            content.append("\n");
            for (Node child : elementChildren) {
                appendXmlNode(child, content, depth + 1);
            }
            content.append(indent);
        }
        content.append("</").append(node.getNodeName()).append(">\n");
    }
}
