/*
 * Copyright (c)2015 Elsevier, Inc.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.elsevier.xml;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;

import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.CharEncoding;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * Class with static methods to filter an XPath expression (return a TRUE/FALSE)
 * against a string of arbitrary xml content and evaluate an XPath expression (return 
 * a serialized response) against a string of arbitrary xml content.
 * 
 * @author Darin McBeath
 * 
 */
public class XPathProcessor {

	// Logger
	private static Log log = LogFactory.getLog(XPathProcessor.class);

	
	/**
	 * Init the NamespaceContext. Set the prefix to namespace mappings specified 
	 * in the passed HashMap.  
	 * 
	 * @param prefixesNamespaces HashMap of namespace prefix / namespace uri mappings
	 */
	public static void init(HashMap<String,String> prefixesNamespaces) throws IOException {

		NamespaceContextMappings.init(prefixesNamespaces);

	}

	/**
	 * Clear namespace prefix / namespace uri mappings. Useful if you want to use a 
	 * different set of prefix/namespace mappings.
	 */
	public static void clear() {

		NamespaceContextMappings.emptyCache();

	}


	/**
	 * Filter the xpathExpression to the specified string.
	 * 
	 * @param content
	 *            String to which the xpathExpression will be applied
	 * @param xpathExpression
	 *            XPath expression to apply to the content
	 * @return TRUE if the xpathExpression evaluates to true, FALSE otherwise
	 */
	public static boolean filterString(String content, String xpathExpression) {

		try {

			return filter(
					new StreamSource(IOUtils.toInputStream(content,
							CharEncoding.UTF_8)), xpathExpression);

		} catch (IOException e) {
			
			log.error("Problems processing the content.  " + e.getMessage(),e);
			return false;
			
		}

	}

	
	/**
	 * Apply the xpathExpression to the specified string and return a serialized
	 * response.
	 * 
	 * @param content
	 *            String to which the xpathExpression will be applied
	 * @param xpathExpression
	 *            XPath expression to apply to the content
	 * @return Serialized response from the evaluation.  If an error, the response will be "<error/>".
	 */
	public static String evaluateString(String content, String xpathExpression) {

		try {

			return evaluate(
					new StreamSource(IOUtils.toInputStream(content,
							CharEncoding.UTF_8)), xpathExpression);

		} catch (IOException e) {
			
			log.error("Problems processing the content.  " + e.getMessage(),e);
			return "<error/>";
			
		}

	}

	
	/**
	 * Filter the xpathExpression to the specified content
	 * 
	 * @param content
	 *            content to which the xpathExpression will be applied
	 * @param xpathExpression
	 *            XPath expression to apply to the content
	 * @return TRUE if the xpathExpression evaluates to true, FALSE otherwise
	 */
	private static boolean filter(StreamSource content, String xpathExpression) {

		try {

			// Get the processor
			Processor proc = new Processor(false);

			// Get the xpath compiler
			XPathCompiler xpathCompiler = proc.newXPathCompiler();

			// Set the namespace to prefix mappings
			setPrefixNamespaceMappings(xpathCompiler);

			XPathSelector xsel = xpathCompiler.compile(xpathExpression).load();

			// Set the source document for which the xpath expression should be
			// applied
			DocumentBuilder builder = proc.newDocumentBuilder();
			XdmNode xmlDoc = builder.build(content);
			xsel.setContextItem(xmlDoc);

			// Evaluate return the boolean value for the xpath expression
			return xsel.effectiveBooleanValue();

		} catch (SaxonApiException e) {
			
			log.error("Problems processing the content.  EXPRESSION:" + xpathExpression + " "  + e.getMessage(),e);
			return false;
			
		}

	}

	
	/**
	 * Apply the xpathExpression to the specified content and return a serialized
	 * response.
	 * 
	 * @param content
	 *            content to which the xpathExpression will be applied
	 * @param xpathExpression
	 *            XPath expression to apply to the content
	 * @return Serialized response from the evaluation.  If an error, the response will be "<error/>".
	 */
	private static String evaluate(StreamSource content, String xpathExpression) {

		try {

			// Get the processor
			Processor proc = new Processor(false);

			// Get the xpath compiler
			XPathCompiler xpathCompiler = proc.newXPathCompiler();

			// Set the namespace to prefix mappings
			setPrefixNamespaceMappings(xpathCompiler);

			XPathSelector xsel = xpathCompiler.compile(xpathExpression).load();

			// Set the source document for which the xpath expression should be applied
			DocumentBuilder builder = proc.newDocumentBuilder();
			XdmNode xmlDoc = builder.build(content);
			xsel.setContextItem(xmlDoc);

			// Create an output serializer
			Serializer out = new Serializer();
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			out.setOutputStream(baos);
			// Appears ok to always set output property to xml (even if we are just returning a text string)
			out.setOutputProperty(Serializer.Property.METHOD, "xml");
			out.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION,"yes");			
			out.setProcessor(proc);

			// Evaluate the xpath expression
			XdmValue results = xsel.evaluate();
			Iterator<XdmItem> it = results.iterator();
			while (it.hasNext()) {
				XdmItem item = it.next();
				out.serializeXdmValue(item);
			}

			// Return the results
			return new String(baos.toByteArray(), CharEncoding.UTF_8);

		} catch (IOException e) {
			
			log.error("Problems processing the content.  EXPRESSION:" + xpathExpression + " "  + e.getMessage(),e);
			return "<error/>";
			
		} catch (SaxonApiException e) {
			
			log.error("Problems processing the content.  EXPRESSION:" + xpathExpression + " "  + e.getMessage(),e);
			return "<error/>";
			
		}

	}

	
	/**
	 * Set the namespaces in the XPathCompiler.
	 * 
	 * @param xpathCompiler
	 */
	private static void setPrefixNamespaceMappings(XPathCompiler xpathCompiler) {

		// Get the mappings
		Set<Entry<String, String>> mappings = NamespaceContextMappings.getMappings();			

		// If mappings exist, set the namespaces
		if (mappings != null) {
			
			Iterator<Entry<String, String>> it = mappings.iterator();
			while (it.hasNext()) {
				Entry<String, String> entry = it.next();
				xpathCompiler.declareNamespace(entry.getKey(), entry.getValue());					
			}
		}

	}

}
