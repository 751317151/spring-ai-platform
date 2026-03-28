package com.huah.ai.platform.rag.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredDocumentParserTest {

    private final StructuredDocumentParser parser = new StructuredDocumentParser(new ObjectMapper());

    @Test
    void parseCsvBuildsStructuredSummary() {
        ByteArrayResource resource = new ByteArrayResource("name,score\nalice,98\nbob,87".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "dataset.csv";
            }
        };

        List<Document> documents = parser.parseCsv(resource);

        assertEquals(1, documents.size());
        Document document = documents.get(0);
        assertTrue(document.getText().contains("Row 1: name=alice; score=98;"));
        assertEquals("csv", document.getMetadata().get("structured_type"));
        assertEquals(2, document.getMetadata().get("row_count"));
        assertEquals(2, document.getMetadata().get("column_count"));
    }

    @Test
    void parseJsonFlattensNestedPaths() {
        ByteArrayResource resource = new ByteArrayResource("""
                {
                  "profile": {
                    "name": "alice",
                    "skills": ["java", "spring"]
                  }
                }
                """.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "profile.json";
            }
        };

        List<Document> documents = parser.parseJson(resource);

        assertFalse(documents.isEmpty());
        assertTrue(documents.stream().anyMatch(item -> item.getText().contains("root.profile.name = alice")));
        assertTrue(documents.stream().anyMatch(item -> item.getText().contains("root.profile.skills[0] = java")));
        assertTrue(documents.stream().allMatch(item -> "json".equals(item.getMetadata().get("structured_type"))));
    }

    @Test
    void parseXmlBuildsReadableTree() {
        ByteArrayResource resource = new ByteArrayResource("""
                <users>
                  <user id="1">
                    <name>alice</name>
                  </user>
                </users>
                """.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "users.xml";
            }
        };

        List<Document> documents = parser.parseXml(resource);

        assertEquals(1, documents.size());
        Document document = documents.get(0);
        assertTrue(document.getText().contains("<users>"));
        assertTrue(document.getText().contains("<user id=\"1\">"));
        assertTrue(document.getText().contains("<name>alice</name>"));
        assertEquals("xml", document.getMetadata().get("structured_type"));
        assertEquals("users", document.getMetadata().get("root_element"));
    }
}
