package de.unigoettingen.sub.search.opac;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/*******************************************************************************
 * Connects to OPAC system.
 * 
 * TODO Talk with the GBV if the URLs are ok this way
 * TODO check if correct character encodings are returned 
 * 
 * @author Ludwig
 * @version 0.1
 * @see
 * @since 0.1.1
 ******************************************************************************/

/**************************************************************************
 * CHANGELOG: 19.07.2005 Ludwig: first Version
 *************************************************************************/

public class GetOpac {
	// the output xml
	public static final String PICA_COLLECTION_RECORDS = "collection";

	public static final String PICA_RECORD = "record";

	public static final String PICA_FIELD = "field";

	public static final String PICA_FIELD_NAME = "tag";

	public static final String PICA_FIELD_OCCURENCES = "occurrence";

	public static final String PICA_SUBFIELD = "subfield";

	private static final String PICA_SUBFIELD_NAME = "code";

	// public static final String PICA_SUBFIELD_NAME = "subfield";
	// public static final String PICA_SUBFIELD = "value";
	// public static final String PICA_FIELD = "picaplus";
	// public static final String PICA_FIELD_NAME = "field";
	// public static final String PICA_RECORD = "picaplusrecord";

	// the opac url parts
	private static final String SET_ID_URL = "/SET=";

	private static final String PICAPLUS_XML_URL_WITHOUT_LOCAL_DATA = "/XML=1.0/CHARSET=";

	/**
	 * The url path part for retrieving picaplus as xml before the value of the response charset
	 */
	private static final String PICAPLUS_XML_URL = "/XML=1.0/PRS=PP%7F" + "/CHARSET=";
	private static final String DATABASE_URL = "/DB=";
	/** The url part for a session id */
	private static final String SESSIONID_URL = "/SID=";
	/** The url part for searching in a specified key field */
	private static final String SEARCH_URL_BEFORE_QUERY = "/CMD?ACT=SRCHM&";
	private static final String SORT_BY_RELEVANCE = "SRT=RLV";
	private static final String SORT_BY_YEAR_OF_PUBLISHING = "SRT=YOP";
	/** the url part for getting the complete data set */
	private static final String SHOW_LONGTITLE_NR_URL = "/SHW?FRST=";
	private static final String SHOW_NEXT_HITS_URL = "/NXT?FRST=";

	/**
	 * Character encoding of the url. "utf-8" is w3c recommendation, but only "iso-8859-1" worked for me.
	 */
	// TODO: Check if this needed.
	public static final String URL_CHARACTER_ENCODING = "iso-8859-1";

	// resources
	private HttpClient opacClient;
	private DocumentBuilder docBuilder;

	// STATE (Instance variables) *****************************************
	// This is now configured inside the Catalogue class.
	// TODO: Check if this should really be query specific
	private String data_character_encoding = "iso-8859-1";

	private Catalogue cat;

	private boolean verbose = false;
	private String sorting = SORT_BY_YEAR_OF_PUBLISHING;

	// for caching the last query and its result
	// TODO decide which result to cache (long or shortlist)? up to now its
	// shortlist so that caching is in principal only used for sessionid and
	// searchopac. is it reasonable?
	private String lastQuery = "";
	// private String sessionID = "";
	// //set id can differ although session id stays the same
	// private String setID = "";

	private OpacResponseHandler lastOpacResult = null;

	// CREATION (Constructors, factory methods, static/inst init)

	/**********************************************************************
	 * Constructor.
	 * 
	 * Note that up to now the search item list is always retrieved and parsed. TODO check for local availability.
	 * 
	 * @param serverAddress
	 *            the serveraddress of the opac
	 * @param port
	 *            the port of the opac
	 * @throws IOException
	 *             If connection to catalogue system failed
	 * @since 0.1
	 *********************************************************************/

	public GetOpac(Catalogue opac) throws ParserConfigurationException {
		super();
		this.opacClient = new HttpClient();
		this.cat = opac;
		this.docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
	}

	/**********************************************************************
	 * Default constructor for the sub goettingen opac.
	 * 
	 * Note that up to now the search item list is always retrieved and parsed. TODO check for local availability.
	 * 
	 * @throws IOException
	 *             If connection to catalogue system failed
	 * @since 0.1
	 *********************************************************************/

	public GetOpac() throws ParserConfigurationException, IOException {
		super();
		this.opacClient = new HttpClient();
		this.docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		// TODO: Remove this default catalogue.
		this.cat = new Catalogue(Catalogue.SUB_OPAC);
	}

	// MANIPULATION (Manipulation - what the object does) ******************

	/***********************************************************************
	 * Gets the number of hits for the query in the specified field from the OPAC.
	 * 
	 * @param query
	 *            The query string you are looking for.
	 * @return returns the number of hits.
	 * @throws Exception
	 *             If something is wrong with the query
	 * @throws IOException
	 *             If connection to catalogue system failed
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 **********************************************************************/
	public int getNumberOfHits(Query query) throws IOException, SAXException, ParserConfigurationException {
		getResult(query);
		return lastOpacResult.getNumberOfHits();
	}

	/************************************************************************
	 * Retrieve the value of a specific field and subfield of a record.
	 * 
	 * @param picaRecord
	 * @param field
	 * @param subfield
	 * @return
	 ***********************************************************************/
	public static String getDataFromPica(Element picaRecord, String field, String occurence, String subfield) {
		return getDataFromPica(picaRecord.getElementsByTagName(PICA_FIELD), field, occurence, subfield);
	}

	/************************************************************************
	 * Retrieve the value of a specific field and subfield of a list of nodes.
	 * 
	 * @param picaFields
	 * @param field
	 * @param subfield
	 * @return
	 ***********************************************************************/
	public static String getDataFromPica(NodeList picaFields, String field, String occurence, String subfield) {
		String result = null;
		for (int i = 0; i < picaFields.getLength(); i++) {
			if ((picaFields.item(i).getNodeType() == Node.ELEMENT_NODE) && ((Element) picaFields.item(i)).hasAttribute(PICA_FIELD_NAME)
					&& (((Element) picaFields.item(i)).getAttribute(PICA_FIELD_NAME).equals(field))
					&& ((Element) picaFields.item(i)).hasAttribute(PICA_FIELD_OCCURENCES)
					&& (((Element) picaFields.item(i)).getAttribute(PICA_FIELD_OCCURENCES).equals(occurence))) {
				NodeList values = ((Element) picaFields.item(i)).getElementsByTagName(PICA_SUBFIELD);
				for (int j = 0; j < values.getLength(); j++) {
					if (((Element) values.item(j)).getAttribute(PICA_SUBFIELD_NAME).equals(subfield)) {
						result = values.item(j).getFirstChild().getNodeValue();
					}
				}
			}
		}
		return result;
	}

	/***********************************************************************
	 * Gets the formated picaplus data of the specified hits for the query in the specified field from the OPAC.
	 * 
	 * @param query
	 *            The query string you are looking for.
	 * @param fieldKey
	 *            The pica mnemonic key (PPN, THM, etc.) for the pica field where the query should be found.
	 * @param numberOfHits
	 *            the number of hits to return. Set to 0 to return all hits.
	 * @return returns the root node of the retrieved and formated xml.
	 * @throws IOException
	 *             If connection to catalogue system failed
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 **********************************************************************/
	public Node retrievePicaNode(Query query, int numberOfHits) throws IOException, SAXException, ParserConfigurationException {
		return retrievePicaNode(query, 0, numberOfHits);
	}

	/************************************************************************
	 * Gets the formated picaplus data of the specified hits for the query from the OPAC.
	 * 
	 * @param query
	 *            The query you are looking for.
	 * @param start
	 *            The index of the first result to be returned
	 * @param end
	 *            The index of the first result NOT to be returned
	 * @return returns the root node of the retrieved and formated xml.
	 * @throws IOException
	 *             If connection to catalogue system failed
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 ***********************************************************************/
	public Node retrievePicaNode(Query query, int start, int end) throws IOException, SAXException, ParserConfigurationException {
		return getParsedDocument(new InputSource(new StringReader(retrievePica(query, start, end)))).getDocumentElement();
	}

	/***********************************************************************
	 * Gets the formated picaplus data of the specified hits for the query in the specified field from the OPAC.
	 * 
	 * @param query
	 *            The query string you are looking for.
	 * @param fieldKey
	 *            The pica mnemonic key (PPN, THM, etc.) for the pica field where the query should be found.
	 * @param numberOfHits
	 *            the number of hits to return. Set to 0 to return all hits.
	 * @return returns the root node of the retrieved and formated xml.
	 * @throws IOException
	 *             If connection to catalogue system failed
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 **********************************************************************/
	public String retrievePica(Query query, int numberOfHits) throws IOException, SAXException, ParserConfigurationException {
		return retrievePica(query, 0, numberOfHits);
	}

	/***********************************************************************
	 * Gets the raw picaplus data for the specified hits for the query in the specified field from the OPAC.
	 * 
	 * @param query
	 *            The query you are looking for.
	 * @param start
	 *            The index of the first result to be returned
	 * @param end
	 *            The index of the first result NOT to be returned. Set to zero to return all hits from the start.
	 * @return returns the root node of the retrieved xml. Beware, it is raw and pretty messy! It is recommended that you use retrieveXMLPicaPlus()
	 * @throws IOException
	 *             If connection to catalogue system failed
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 **********************************************************************/
	public String retrievePica(Query query, int start, int end) throws IOException, SAXException, ParserConfigurationException {
		StringBuffer xmlResult = new StringBuffer();

		// querySummary is used to check if cached result and sessionid
		// can be used again
		String querySummary = query.getQueryUrl() + data_character_encoding + cat.getDataBase() + cat.getServerAddress()
				 + cat.getPort()+ cat.getCbs();

		// if we can not use the cached result
		if (!lastQuery.equals(querySummary)) {
			// then we need a new sessionid and resultstring
			getResult(query);
		}

		// make sure that upper limit of requested hits is not to high
		int maxNumberOfHits = lastOpacResult.getNumberOfHits();
		if (end > maxNumberOfHits) {
			end = maxNumberOfHits;
		}
		// return all hits if requested
		if (end == 0) {
			end = maxNumberOfHits;
		}

		xmlResult.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		xmlResult.append("  <" + PICA_COLLECTION_RECORDS + ">\n");

		// String xmlPicaPlusLong = null;
		// retrieve and append the requested hits
		for (int i = start; i < end; i++) {

			xmlResult.append(xmlFormatPica(retrievePicaTitle(i)));

			// if (xmlPicaPlusLong != null){
			// xmlResult.append(xmlPicaPlusLong);
			// }else{
			// System.err.println("Could not retrieve data for hit nr:" + i);
			// }
		}
		xmlResult.append("  </" + PICA_COLLECTION_RECORDS + ">\n");

		// //check if we got an result
		// if (((Element)result).hasAttribute("error")){
		// //if it was not an error because of no hits getNumberOfHits
		// //throws an exception
		// if (getNumberOfHits(query) == 0){
		// throw new Exception("No Hits");
		// }
		//
		// }

		return xmlResult.toString();
	}

	/***********************************************************************
	 * Gets the raw picaplus data for the specified hits for the query in the specified field from the OPAC.
	 * 
	 * @param query
	 *            The query string you are looking for.
	 * @param start
	 *            The index of the first result to be returned
	 * @param end
	 *            The index of the first result NOT to be returned. Set to zero to return all hits from the start.
	 * @return returns the root node of the retrieved xml. Beware, it is raw and pretty messy! It is recommended that you use retrieveXMLPicaPlus()
	 * @throws IOException
	 *             If connection to catalogue system failed
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IllegalQueryException
	 **********************************************************************/
	public Node retrievePicaRawNode(Query query, int start, int end) throws IOException, SAXException, ParserConfigurationException,
			IllegalQueryException {
		Node result = null;

		// querySummary is used to check if cached result and sessionid
		// can be used again
		String querySummary = query.getQueryUrl() + data_character_encoding + cat.getDataBase() + cat.getServerAddress() + cat.getPort()+ cat.getCbs();

		// if we can not use the cached result
		if (!lastQuery.equals(querySummary)) {
			// then we need a new sessionid and resultstring
			getResult(query);
		}

		// this will be our new picaplus xml-document and its root
		Document picaPlusRaw = getNewDocument();
		result = picaPlusRaw.createElement("picaplusrawresults");
		// formating
		result.appendChild(picaPlusRaw.createTextNode("\n"));

		// make sure that upper limit of requested hits is not to high
		int maxNumberOfHits = lastOpacResult.getNumberOfHits() + 1;
		if (end > maxNumberOfHits) {
			end = maxNumberOfHits;
		}
		// return all hits if requested
		if (end == 0) {
			end = maxNumberOfHits;
		}

		// retrieve and append the requested hits
		for (int i = start; i < end; i++) {
			Node picaPlusLong = retrievePicaTitleNode(i);
			if (picaPlusLong != null) {
				result.appendChild(picaPlusRaw.importNode(picaPlusLong, true));
			} else {
				System.err.println("Could not retrieve data for hit nr:" + i);
			}
		}

		// formating
		result.appendChild(picaPlusRaw.createTextNode("\n"));

		// check if we got an result
		if (((Element) result).hasAttribute("error")) {
			// if it was not an error because of no hits getNumberOfHits
			// throws an exception
			if (getNumberOfHits(query) == 0) {
				throw new IllegalQueryException("No Hits");
			}

		}

		return result;
	}

	/***********************************************************************
	 * Retrieves a single hit from the catalogue system.
	 * 
	 * @param numberOfHit
	 *            The index of the hit to return
	 * @return The response as the root node of an xml tree
	 * @throws IOException
	 *             If the connection failed
	 **********************************************************************/
	private Node retrievePicaTitleNode(int numberOfHit) throws IOException {
		String resultString = null;
		// get pica longtitle
		resultString = retrievePicaTitle(numberOfHit);
		// parse result as xml, append result to the root node
		InputSource resultSource = new InputSource(new StringReader(resultString));
		Document parsedDoc = getParsedDocument(resultSource);
		if (parsedDoc == null) {
			return null;
		}
		return parsedDoc.getDocumentElement();
	}

	/***********************************************************************
	 * Retrieves a single hit from the catalogue system.
	 * 
	 * @param numberOfHits
	 *            The index of the hit to return
	 * @throws IOException
	 *             If the connection failed
	 **********************************************************************/
	private String retrievePicaTitle(int numberOfHits) throws IOException {
		// get pica longtitle
		int retrieveNumber = numberOfHits + 1;
		return retrieveDataFromOPAC(DATABASE_URL + cat.getDataBase() + PICAPLUS_XML_URL + data_character_encoding + SET_ID_URL
				+ lastOpacResult.getSet() + SESSIONID_URL + URLEncoder.encode(lastOpacResult.getSessionId(), URL_CHARACTER_ENCODING)
				+ SHOW_LONGTITLE_NR_URL + retrieveNumber);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List[] getResultLists(Query query, int numberOfHits) throws IOException, SAXException, ParserConfigurationException {
		List[] result = new List[2];
		OpacResponseHandler search = getResult(query);

		// return all hits?
		if (numberOfHits == 0) {
			numberOfHits = search.getNumberOfHits();
		}
		// PPN list
		result[0] = new ArrayList(numberOfHits);
		// title list
		result[1] = new ArrayList(numberOfHits);
		result[0].addAll(search.getOpacResponseItemPpns());
		result[1].addAll(search.getOpacResponseItemTitles());

		// return more than the first 10 hits
		for (int i = 10; i < numberOfHits; i += 10) {
			String tmpSearch = retrieveDataFromOPAC("/XML=1.0" + DATABASE_URL + cat.getDataBase() + SET_ID_URL + search.getSet() + SESSIONID_URL
					+ search.getSessionId() + "/TTL=" + (i - 9) + SHOW_NEXT_HITS_URL + (i + 1));
			search = parseOpacResponse(tmpSearch);
			result[0].addAll(search.getOpacResponseItemPpns());
			result[1].addAll(search.getOpacResponseItemTitles());
		}

		return result;
	}

	/***********************************************************************
	 * Queries the catalogue system.
	 * 
	 * @param query
	 *            The query you are looking for.
	 * @return The search result as xml string.
	 * @throws IOException
	 *             If connection to catalogue system failed.
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 **********************************************************************/
	public OpacResponseHandler getResult(Query query) throws IOException, SAXException, ParserConfigurationException {
		String result = null;

		String querySummary = query.getQueryUrl() + data_character_encoding + cat.getDataBase() + cat.getServerAddress() + cat.getPort()+ cat.getCbs();

		if (verbose) {
			System.out.println("Searching the opac for " + query.getQueryUrl());
		}

		if (lastQuery.equals(querySummary)) {
			if (verbose) {
				System.out.println("Using cached result because last query was: " + querySummary);
			}
			return lastOpacResult;
		}
		result = retrieveDataFromOPAC(DATABASE_URL + cat.getDataBase() + PICAPLUS_XML_URL_WITHOUT_LOCAL_DATA + data_character_encoding
				+ SEARCH_URL_BEFORE_QUERY + sorting + query.getQueryUrl());

		OpacResponseHandler opacResult = parseOpacResponse(result);

		// Caching query, result and sessionID
		lastQuery = querySummary;
		lastOpacResult = opacResult;

		return opacResult;
	}

	// INTERNAL (Internal - implementation details, local classes, ...)

	// private String xmlFormatPica(String picaXmlRecord) {
	// System.out.println(picaXmlRecord);
	// StringBuffer result = new StringBuffer("  <" + PICA_RECORD + ">\n");
	// int startField = 0;
	// int nextField = 0;
	// int endField = picaXmlRecord.indexOf("</LONGTITLE>");
	// String field = null;
	// String[] fieldComponents = null;
	// String fieldName = null;
	// String fieldOccurence = null;
	// int indexOfFieldOccurence = -1;
	//
	// while (nextField != endField) {
	// startField = picaXmlRecord.indexOf("<br />", startField) + 6;
	// nextField = picaXmlRecord.indexOf("<br />", startField);
	// if (nextField == -1) {
	// nextField = endField;
	// }
	// field = picaXmlRecord.substring(startField, nextField).trim();
	// fieldComponents = field.split("\\$");
	// indexOfFieldOccurence = fieldComponents[0].indexOf("/");
	//
	// if (indexOfFieldOccurence != -1) {
	// fieldName = fieldComponents[0].substring(0, indexOfFieldOccurence);
	// fieldOccurence = fieldComponents[0].substring(indexOfFieldOccurence + 1);
	// result.append("    <" + PICA_FIELD + " " + PICA_FIELD_NAME + "=\"" + fieldName + "\" "
	// + PICA_FIELD_OCCURENCES + "=\"" + fieldOccurence + "\">\n");
	// } else {
	// result
	// .append("    <" + PICA_FIELD + " " + PICA_FIELD_NAME + "=\"" + fieldComponents[0] + "\">\n");
	// }
	//
	// for (int i = 1; i < fieldComponents.length; i++) {
	// result.append("      <" + PICA_SUBFIELD + " " + PICA_SUBFIELD_NAME + "=\""
	// + fieldComponents[i].charAt(0) + "\">" + fieldComponents[i].substring(1) + "</"
	// + PICA_SUBFIELD + ">\n");
	// }
	//
	// result.append("    </" + PICA_FIELD + ">\n");
	// }
	//
	// result.append("  </" + PICA_RECORD + ">\n");
	// System.out.println("--------------------------------\n\n"+ result.toString());
	// return result.toString();
	// }

	private String xmlFormatPica(String picaXmlRecord) {
		StringBuffer result = new StringBuffer("  <" + PICA_RECORD + ">\n");
		try {
			int startField = picaXmlRecord.indexOf("LONGTITLE");
			int nextField = 0;
			int endField = picaXmlRecord.indexOf("</LONGTITLE>");
			String field = picaXmlRecord.substring(startField, endField);

			// for some unknown reason the line break/record separator is
			// sometimes different
			String recordSeperator = "<br />";
			if (picaXmlRecord.indexOf(recordSeperator) != -1) {
				while (nextField != endField) {
					startField = picaXmlRecord.indexOf(recordSeperator, startField) + 6;
					nextField = picaXmlRecord.indexOf(recordSeperator, startField);
					if (nextField == -1) {
						nextField = endField;
					}
					field = picaXmlRecord.substring(startField, nextField).trim();
					result.append(parseRecordField(field));
				}
			} else {
				String[] lines = field.split("\n");
				for (int i = 1; i < lines.length; i++) {
					result.append(parseRecordField(lines[i]));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		result.append("  </" + PICA_RECORD + ">\n");
		return result.toString();
	}

	private StringBuffer parseRecordField(String field) {
		StringBuffer result = new StringBuffer();

		String[] fieldComponents = null;
		String fieldName = null;
		String fieldOccurence = null;
		int indexOfFieldOccurence = -1;

		fieldComponents = field.split("\\$");
		indexOfFieldOccurence = fieldComponents[0].indexOf("/");

		if (indexOfFieldOccurence != -1) {
			fieldName = fieldComponents[0].substring(0, indexOfFieldOccurence);
			fieldOccurence = fieldComponents[0].substring(indexOfFieldOccurence + 1);
			result.append("    <" + PICA_FIELD + " " + PICA_FIELD_NAME + "=\"" + fieldName + "\" " + PICA_FIELD_OCCURENCES + "=\"" + fieldOccurence
					+ "\">\n");
		} else {
			result.append("    <" + PICA_FIELD + " " + PICA_FIELD_NAME + "=\"" + fieldComponents[0] + "\">\n");
		}

		for (int i = 1; i < fieldComponents.length; i++) {
			result.append("      <" + PICA_SUBFIELD + " " + PICA_SUBFIELD_NAME + "=\"" + fieldComponents[i].charAt(0) + "\">"
					+ fieldComponents[i].substring(1) + "</" + PICA_SUBFIELD + ">\n");
		}

		result.append("    </" + PICA_FIELD + ">\n");
		return result;
	}

	/***********************************************************************
	 * Helper method that parses an InputSource and returns a DOM Document.
	 * 
	 * @param source
	 *            The InputSource to parse
	 * @return The resulting document
	 **********************************************************************/
	public Document getParsedDocument(InputSource source) {
		try {
			return this.docBuilder.parse(source);
		} catch (SAXException e) {
			System.out.println("Dokument?");

			InputStream bs = source.getByteStream();
//			StreamResult output = new StreamResult(System.out);

			System.out.println(bs.toString());
			e.printStackTrace();

		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/***********************************************************************
	 * Helper method that returns a new XML Document.
	 * 
	 * @return the nex Document
	 **********************************************************************/
	private Document getNewDocument() {

		return this.docBuilder.newDocument();
		// return null;
	}

	/***********************************************************************
	 * Helper method that prints a DOM Tree to System.out.
	 * 
	 * @param source
	 *            The DOMSource to print
	 **********************************************************************/
	public void outputXMLTreeToSysout(DOMSource source) {
		try {
			TransformerFactory tFac = TransformerFactory.newInstance();
			Transformer transformer = tFac.newTransformer();
			StreamResult output = new StreamResult(System.out);

			transformer.setOutputProperty(OutputKeys.ENCODING, data_character_encoding);
			transformer.transform(source, output);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/***********************************************************************
	 * Retrieves the content of the specified url from the serverAddress.
	 * 
	 * @param url
	 *            The requested url as string. Note that the string needs to be already url encoded.
	 * @return The response.
	 * @throws IOException
	 *             If the connection failed
	 **********************************************************************/
	private String retrieveDataFromOPAC(String url) throws IOException {

		// if (verbose){
		System.out.println("Retrieving URL: http://" + cat.getServerAddress() + ":" + cat.getPort()  + url + cat.getCbs());
		// }
		GetMethod opacRequest = new GetMethod("http://" + cat.getServerAddress() + ":" + cat.getPort() + url + cat.getCbs());

		try {
			this.opacClient.executeMethod(opacRequest);
			return opacRequest.getResponseBodyAsString();
		} finally {
			opacRequest.releaseConnection();
		}

	}

	public OpacResponseHandler parseOpacResponse(String opacResponse) throws IOException, SAXException, ParserConfigurationException {
		opacResponse = opacResponse.replace("&amp;amp;", "&amp;").replace("&amp;quot;", "&quot;").replace("&amp;lt;", "&lt;").replace("&amp;gt;", "&gt;");
		
		XMLReader parser = null;
		OpacResponseHandler ids = new OpacResponseHandler();
		/* Use Java 1.4 methods to create default parser. */
		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);
		parser = factory.newSAXParser().getXMLReader();

		parser.setContentHandler(ids);
		parser.parse(new InputSource(new StringReader(opacResponse)));

		return ids;
	}

	public Catalogue getCat() {
		return cat;
	}

	public void setCat(Catalogue opac) {
		this.cat = opac;
	}

	/***********************************************************************
	 * Set requested character encoding for the response of the catalogue system. For goettingen iso-8859-1 and utf-8 work, the default is iso-8859-1.
	 * 
	 * @param data_character_encoding
	 *            The character encoding to set.
	 **********************************************************************/

	// TODO: rename this Method to camelCase convention
	public void setData_character_encoding(String data_character_encoding) {
		this.data_character_encoding = data_character_encoding;
	}

	/***********************************************************************
	 * Set verbose to true to get debug messages printed to System.out.
	 * 
	 * @param verbose
	 *            True will deliver debug messages to System.out.
	 **********************************************************************/

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public void sortByRelevance() {
		sorting = SORT_BY_RELEVANCE;
	}

	public void sortByYearOfPublishing() {
		sorting = SORT_BY_YEAR_OF_PUBLISHING;
	}

}
