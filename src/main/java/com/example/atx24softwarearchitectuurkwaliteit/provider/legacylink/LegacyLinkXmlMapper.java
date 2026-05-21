package com.example.atx24softwarearchitectuurkwaliteit.provider.legacylink;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility klasse voor het serialiseren naar, en deserialiseren vanuit het LegacyLink XML-formaat.
 * Waar modernere providers (zoals json/rest) vaak libraries als Jackson gebruiken, is hier
 * gekozen voor een expliciete low-level StringBuilder- en DOM-parseroplossing.
 * Dit is robuust én extra veilig ingesteld om XXE (XML eXternal Entity) vulnerabilities
 * in de parsing via FEATURE_SECURE_PROCESSING te voorkomen (een security best-practice).
 */
final class LegacyLinkXmlMapper {

    private static final String NAMESPACE = "http://legacylink.fakecomworld.com/v1";
    private static final Pattern MESSAGE_REFERENCE_PATTERN = Pattern.compile("<MessageReference>(.*?)</MessageReference>", Pattern.DOTALL);

    private LegacyLinkXmlMapper() {
    }

    static String toXml(LegacyLinkRequest request) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        xml.append("<SendSmsRequest xmlns=\"").append(NAMESPACE).append("\">");
        xml.append("<PhoneNumber>").append(escapeXml(request.getPhoneNumber())).append("</PhoneNumber>");
        xml.append("<MessageText>").append(escapeXml(request.getMessageText())).append("</MessageText>");
        if (request.getSenderIdentification() != null && !request.getSenderIdentification().isBlank()) {
            xml.append("<SenderIdentification>").append(escapeXml(request.getSenderIdentification())).append("</SenderIdentification>");
        }
        xml.append("</SendSmsRequest>");
        return xml.toString();
    }

    static LegacyLinkResponse fromXml(String responseXml) {
        LegacyLinkResponse response = new LegacyLinkResponse();
        response.setStatusCode(readInt(responseXml, "StatusCode"));
        response.setStatusMessage(readString(responseXml, "StatusMessage"));
        response.setMessageReference(readString(responseXml, "MessageReference"));

        String timestampValue = readString(responseXml, "Timestamp");
        if (timestampValue != null && !timestampValue.isBlank()) {
            response.setTimestamp(Instant.parse(timestampValue));
        }

        return response;
    }

    private static String readString(String xml, String tagName) {
        Document document = parseDocument(xml);
        NodeList nodes = document.getElementsByTagNameNS("*", tagName);
        if (nodes.getLength() == 0) {
            nodes = document.getElementsByTagName(tagName);
        }
        if (nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent();
    }

    private static int readInt(String xml, String tagName) {
        String value = readString(xml, tagName);
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Integer.parseInt(value.trim());
    }

    private static Document parseDocument(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new LegacyLinkException("Unable to parse LegacyLink XML response: " + e.getMessage());
        }
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }

        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}