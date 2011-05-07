/*

Copyright 2010, Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
    * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,           
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY           
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package com.google.refine.importers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.servlet.ServletException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.refine.ProjectMetadata;
import com.google.refine.importers.tree.ImportColumnGroup;
import com.google.refine.importers.tree.TreeImportingParserBase;
import com.google.refine.importers.tree.TreeReader;
import com.google.refine.importing.ImportingJob;
import com.google.refine.importing.ImportingUtilities;
import com.google.refine.model.Project;
import com.google.refine.util.JSONUtilities;

public class XmlImporter extends TreeImportingParserBase {
    public XmlImporter() {
        super(true);
    }
    
    static private class PreviewParsingState {
        int tokenCount;
    }
    
    final static private int PREVIEW_PARSING_LIMIT = 1000;
    
    @Override
    public JSONObject createParserUIInitializationData(
            ImportingJob job, List<JSONObject> fileRecords, String format) {
        JSONObject options = super.createParserUIInitializationData(job, fileRecords, format);
        try {
            JSONObject firstFileRecord = fileRecords.get(0);
            File file = ImportingUtilities.getFile(job, firstFileRecord);
            InputStream is = new FileInputStream(file);
            try {
                XMLStreamReader parser = createXMLStreamReader(is);
                PreviewParsingState state = new PreviewParsingState();
                
                while (parser.hasNext() && state.tokenCount < PREVIEW_PARSING_LIMIT) {
                    int tokenType = parser.next();
                    state.tokenCount++;
                    if (tokenType == XMLStreamConstants.START_ELEMENT) {
                        JSONObject rootElement = descendElement(parser, state);
                        if (rootElement != null) {
                            JSONUtilities.safePut(options, "dom", rootElement);
                            break;
                        }
                    } else {
                        // ignore everything else
                    }
                }
            } finally {
                is.close();
            }
        } catch (XMLStreamException e) {
            // Ignore
        } catch (IOException e) {
            // Ignore
        }

        return options;
    }
    
    final static private JSONObject descendElement(XMLStreamReader parser, PreviewParsingState state) throws XMLStreamException {
        JSONObject result = new JSONObject();
        JSONUtilities.safePut(result, "n", parser.getName());
        
        int attributeCount = parser.getAttributeCount();
        if (attributeCount > 0) {
            JSONArray attributes = new JSONArray();
            JSONUtilities.safePut(result, "a", attributes);
            
            for (int i = 0; i < attributeCount; i++) {
                JSONObject attribute = new JSONObject();
                JSONUtilities.append(attributes, attribute);
                JSONUtilities.safePut(attribute, "n", parser.getAttributeName(i));
                JSONUtilities.safePut(attribute, "v", parser.getAttributeValue(i));
            }
        }
        
        JSONArray children = new JSONArray();
        while (parser.hasNext() && state.tokenCount < PREVIEW_PARSING_LIMIT) {
            int tokenType = parser.next();
            state.tokenCount++;
            if (tokenType == XMLStreamConstants.END_ELEMENT) {
                break;
            } else if (tokenType == XMLStreamConstants.START_ELEMENT) {
                JSONObject childElement = descendElement(parser, state);
                if (childElement != null) {
                    JSONUtilities.append(children, childElement);
                }
            } else if (tokenType == XMLStreamConstants.CHARACTERS ||
                       tokenType == XMLStreamConstants.CDATA ||
                       tokenType == XMLStreamConstants.SPACE) {
                JSONObject childElement = new JSONObject();
                JSONUtilities.safePut(childElement, "t", parser.getText());
                JSONUtilities.append(children, childElement);
            } else {
                // ignore everything else
            }
        }
        
        if (children.length() > 0) {
            JSONUtilities.safePut(result, "c", children);
        }
        return result;
    }
    
    @Override
    public void parseOneFile(Project project, ProjectMetadata metadata,
            ImportingJob job, String fileSource, InputStream inputStream,
            ImportColumnGroup rootColumnGroup, int limit, JSONObject options,
            List<Exception> exceptions) {
        
        try {
            parseOneFile(project, metadata, job, fileSource,
                new XmlParser(inputStream), rootColumnGroup, limit, options, exceptions);
        } catch (XMLStreamException e) {
            exceptions.add(e);
        }
    }
    
    static public class XmlParser implements TreeReader {
        final protected XMLStreamReader parser;
        
        public XmlParser(InputStream inputStream) throws XMLStreamException {
            parser = createXMLStreamReader(inputStream);
        }
        
        @Override
        public Token next() throws ServletException {
            try {
                if (!parser.hasNext()) {
                    throw new ServletException("End of XML stream");
                }
            } catch (XMLStreamException e) {
                throw new ServletException(e);
            }
            
            int currentToken = -1;
            try {
                currentToken = parser.next();
            } catch (XMLStreamException e) {
                throw new ServletException(e);
            }
            
            return mapToToken(currentToken);
        }
        
        protected Token mapToToken(int token) throws ServletException {
            switch(token){
                case XMLStreamConstants.START_ELEMENT: return Token.StartEntity;
                case XMLStreamConstants.END_ELEMENT: return Token.EndEntity;
                case XMLStreamConstants.CHARACTERS: return Token.Value;
                case XMLStreamConstants.START_DOCUMENT: return Token.Ignorable;
                case XMLStreamConstants.END_DOCUMENT: return Token.Ignorable;
                case XMLStreamConstants.SPACE: return Token.Value;
                case XMLStreamConstants.PROCESSING_INSTRUCTION: return Token.Ignorable;
                case XMLStreamConstants.NOTATION_DECLARATION: return Token.Ignorable;
                case XMLStreamConstants.NAMESPACE: return Token.Ignorable;
                case XMLStreamConstants.ENTITY_REFERENCE: return Token.Ignorable;
                case XMLStreamConstants.DTD: return Token.Ignorable;
                case XMLStreamConstants.COMMENT: return Token.Ignorable;
                case XMLStreamConstants.CDATA: return Token.Ignorable;
                case XMLStreamConstants.ATTRIBUTE: return Token.Ignorable;
                default:
                    return Token.Ignorable;
            }
        }
        
        @Override
        public Token current() throws ServletException{
            return this.mapToToken(parser.getEventType());
        }
        
        @Override
        public boolean hasNext() throws ServletException{
            try {
                return parser.hasNext();
            } catch (XMLStreamException e) {
                throw new ServletException(e);
            }
        }
        
        @Override
        public String getFieldName() throws ServletException{
            try{
                return parser.getLocalName();
            }catch(IllegalStateException e){
                return null;
            }
        }
        
        @Override
        public String getPrefix(){
            return parser.getPrefix();
        }
        
        @Override
        public String getFieldValue(){
            return parser.getText();
        }
        
        @Override
        public int getAttributeCount(){
            return parser.getAttributeCount();
        }
        
        @Override
        public String getAttributeValue(int index){
            return parser.getAttributeValue(index);
        }
        
        @Override
        public String getAttributePrefix(int index){
            return parser.getAttributePrefix(index);
        }
        
        @Override
        public String getAttributeLocalName(int index){
            return parser.getAttributeLocalName(index);
        }
    }
    
    final static private XMLStreamReader createXMLStreamReader(InputStream inputStream) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true);
        
        return factory.createXMLStreamReader(inputStream);
    }
    
}
