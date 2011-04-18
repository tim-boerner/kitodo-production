package de.sub.goobi.Import;

import java.io.IOException;
import java.util.Iterator;
import java.util.StringTokenizer;

import javax.faces.context.FacesContext;

import org.apache.log4j.Logger;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.DOMBuilder;
import org.jdom.output.DOMOutputter;
import org.w3c.dom.Node;

import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.fileformats.mets.XStream;
import ugh.fileformats.opac.PicaPlus;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.UghHelper;
import de.unigoettingen.sub.search.opac.Catalogue;
import de.unigoettingen.sub.search.opac.ConfigOpac;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import de.unigoettingen.sub.search.opac.ConfigOpacDoctype;
import de.unigoettingen.sub.search.opac.GetOpac;
import de.unigoettingen.sub.search.opac.Query;

public class ImportOpac {
	private static final Logger myLogger = Logger.getLogger(ImportOpac.class);

	private int hitcount;
	private String gattung = "Aa";
	private String atstsl;
	ConfigOpacCatalogue coc;

	/**
	 * @param inSuchfeld
	 *            (PPN: 12, ISBN: 7, ISSN: 8, alles: 1016, Verbuchungsnummer:
	 *            8535
	 * @param inSuchbegriff
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public Fileformat OpacToDocStruct(String inSuchfeld, String inSuchbegriff, String inKatalog, Prefs inPrefs) throws Exception {
		/*
		 * -------------------------------- Katalog auswählen
		 * --------------------------------
		 */
		coc = new ConfigOpac().getCatalogueByName(inKatalog);
		if (coc == null)
			throw new IOException("Catalogue not found: " + inKatalog + ", please check Configuration in opac.xml");
		Catalogue cat = new Catalogue(coc.getDescription(), coc.getAddress(), coc.getPort(), coc.getCbs(), coc.getDatabase());
		Helper.setMeldung(null, "verwendeter Katalog: ", coc.getDescription());

		GetOpac myOpac = new GetOpac(cat);
		myOpac.setData_character_encoding(coc.getCharset());
		Query myQuery = new Query(inSuchbegriff, inSuchfeld);
		/* im Notfall ohne Treffer sofort aussteigen */
		hitcount = myOpac.getNumberOfHits(myQuery);
		if (hitcount == 0) {
			return null;
		}

		/*
		 * -------------------------------- Opac abfragen und erhaltenes
		 * Dom-Dokument in JDom-Dokument umwandeln
		 * --------------------------------
		 */
		Node myHitlist = myOpac.retrievePicaNode(myQuery, 1);
		/* Opac-Beautifier aufrufen */
		myHitlist = coc.executeBeautifier(myHitlist);
		Document myJdomDoc = new DOMBuilder().build(myHitlist.getOwnerDocument());
		Element myFirstHit = myJdomDoc.getRootElement().getChild("record");

		/* von dem Treffer den Dokumententyp ermitteln */
		gattung = getGattung(myFirstHit);

		myLogger.debug("Gattung: " + gattung);
		/*
		 * -------------------------------- wenn der Treffer ein Volume eines
		 * Multivolume-Bandes ist, dann das Sammelwerk überordnen
		 * --------------------------------
		 */
		// if (isMultivolume()) {
		if (getOpacDocType().isMultiVolume()) {
			/* Sammelband-PPN ermitteln */
			String multiVolumePpn = getPpnFromParent(myFirstHit, "036D", "9");
			if (multiVolumePpn != "") {
				/* Sammelband aus dem Opac holen */

				myQuery = new Query(multiVolumePpn, "12");
				/* wenn ein Treffer des Parents im Opac gefunden wurde */
				if (myOpac.getNumberOfHits(myQuery) == 1) {
					Node myParentHitlist = myOpac.retrievePicaNode(myQuery, 1);
					/* Opac-Beautifier aufrufen */
					myParentHitlist = coc.executeBeautifier(myParentHitlist);
					/* Konvertierung in jdom-Elemente */
					Document myJdomDocMultivolumeband = new DOMBuilder().build(myParentHitlist.getOwnerDocument());

					/* Testausgabe */
					// XMLOutputter outputter = new XMLOutputter();
					// FileOutputStream output = new
					// FileOutputStream("D:/fileParent.xml");
					// outputter.output(myJdomDocMultivolumeband.getRootElement(),
					// output);
					/* dem Rootelement den Volume-Treffer hinzufügen */
					myFirstHit.getParent().removeContent(myFirstHit);
					myJdomDocMultivolumeband.getRootElement().addContent(myFirstHit);

					/* Testausgabe */
					// output = new FileOutputStream("D:/fileFull.xml");
					// outputter.output(myJdomDocMultivolumeband.getRootElement(),
					// output);
					myJdomDoc = myJdomDocMultivolumeband;
					myFirstHit = myJdomDoc.getRootElement().getChild("record");

					/* die Jdom-Element wieder zurück zu Dom konvertieren */
					DOMOutputter doutputter = new DOMOutputter();
					myHitlist = doutputter.output(myJdomDocMultivolumeband);
					/*
					 * dabei aber nicht das Document, sondern das erste Kind
					 * nehmen
					 */
					myHitlist = myHitlist.getFirstChild();
				}
			}
		}

		/*
		 * -------------------------------- wenn der Treffer ein Contained Work
		 * ist, dann übergeordnetes Werk --------------------------------
		 */
		// if (isContainedWork()) {
		if (getOpacDocType().isContainedWork()) {
			/* PPN des übergeordneten Werkes ermitteln */
			String ueberGeordnetePpn = getPpnFromParent(myFirstHit, "021A", "9");
			if (ueberGeordnetePpn != "") {
				/* Sammelband aus dem Opac holen */
				myQuery = new Query(ueberGeordnetePpn, "12");
				/* wenn ein Treffer des Parents im Opac gefunden wurde */
				if (myOpac.getNumberOfHits(myQuery) == 1) {
					Node myParentHitlist = myOpac.retrievePicaNode(myQuery, 1);
					/* Opac-Beautifier aufrufen */
					myParentHitlist = coc.executeBeautifier(myParentHitlist);
					/* Konvertierung in jdom-Elemente */
					Document myJdomDocParent = new DOMBuilder().build(myParentHitlist.getOwnerDocument());
					Element myFirstHitParent = myJdomDocParent.getRootElement().getChild("record");
					/* Testausgabe */
					// XMLOutputter outputter = new XMLOutputter();
					// FileOutputStream output = new
					// FileOutputStream("D:/fileParent.xml");
					// outputter.output(myJdomDocParent.getRootElement(),
					// output);
					/*
					 * alle Elemente des Parents übernehmen, die noch nicht
					 * selbst vorhanden sind
					 */
					if (myFirstHitParent.getChildren() != null) {

						for (Iterator iter = myFirstHitParent.getChildren().iterator(); iter.hasNext();) {
							Element ele = (Element) iter.next();
							if (getElementFromChildren(myFirstHit, ele.getAttributeValue("tag")) == null)
								myFirstHit.getChildren().add(getCopyFromJdomElement(ele));
						}
					}
				}
			}
		}

		/*
		 * -------------------------------- aus Opac-Ergebnis RDF-Datei erzeugen
		 * --------------------------------
		 */
		/* XML in Datei schreiben */
		// XMLOutputter outputter = new XMLOutputter();
		// FileOutputStream output = new
		// FileOutputStream("c:/Temp/temp_opac.xml");
		// outputter.output(myJdomDoc.getRootElement(), output);
		/* myRdf temporär in Datei schreiben */
		// myRdf.write("D:/temp.rdf.xml");

		/* zugriff auf ugh-Klassen */
		PicaPlus pp = new PicaPlus(inPrefs);
		pp.read(myHitlist);
		DigitalDocument dd = pp.getDigitalDocument();
		Fileformat ff = new XStream(inPrefs);
		ff.setDigitalDocument(dd);
		/* BoundBook hinzufügen */
		DocStructType dst = inPrefs.getDocStrctTypeByName("BoundBook");
		DocStruct dsBoundBook = dd.createDocStruct(dst);
		dd.setPhysicalDocStruct(dsBoundBook);
		/* Inhalt des RDF-Files überprüfen und ergänzen */
		checkMyOpacResult(ff.getDigitalDocument(), inPrefs, myFirstHit);
		// rdftemp.write("D:/PicaRdf.xml");
		return ff;
	}

	/**
	 * DocType (Gattung) ermitteln
	 * 
	 * @param inHit
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private String getGattung(Element inHit) {

		for (Iterator<Element> iter = inHit.getChildren().iterator(); iter.hasNext();) {
			Element tempElement = (Element) iter.next();
			String feldname = tempElement.getAttributeValue("tag");
			// System.out.println(feldname);
			if (feldname.equals("002@"))
				return getSubelementValue(tempElement, "0");
		}
		return "";
	}

	@SuppressWarnings("unchecked")
	private String getSubelementValue(Element inElement, String attributeValue) {
		String rueckgabe = "";

		for (Iterator<Element> iter = inElement.getChildren().iterator(); iter.hasNext();) {
			Element subElement = (Element) iter.next();
			if (subElement.getAttributeValue("code").equals(attributeValue))
				rueckgabe = subElement.getValue();
		}
		return rueckgabe;
	}

	/**
	 * die PPN des übergeordneten Bandes (MultiVolume: 036D-9 und ContainedWork:
	 * 021A-9) ermitteln
	 * 
	 * @param inElement
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private String getPpnFromParent(Element inHit, String inFeldName, String inSubElement) {
		for (Iterator<Element> iter = inHit.getChildren().iterator(); iter.hasNext();) {
			Element tempElement = (Element) iter.next();
			String feldname = tempElement.getAttributeValue("tag");
			// System.out.println(feldname);
			if (feldname.equals(inFeldName))
				return getSubelementValue(tempElement, inSubElement);
		}
		return "";
	}

	public int getHitcount() {
		return hitcount;
	}

	/*
	 * #####################################################
	 * ##################################################### ## ## Erg�nze das
	 * Docstruct um zusätzliche Opac-Details ##
	 * #####################################################
	 * ####################################################
	 */

	private void checkMyOpacResult(DigitalDocument inDigDoc, Prefs inPrefs, Element myFirstHit) {
		UghHelper ughhelp = new UghHelper();
		DocStruct topstruct = inDigDoc.getLogicalDocStruct();
		DocStruct boundbook = inDigDoc.getPhysicalDocStruct();
		DocStruct topstructChild = null;
		Element mySecondHit = null;

		/*
		 * -------------------------------- bei Multivolumes noch das Child in
		 * xml und docstruct ermitteln --------------------------------
		 */
		// if (isMultivolume()) {
		if (getOpacDocType().isMultiVolume()) {
			try {
				topstructChild = topstruct.getAllChildren().get(0);
			} catch (RuntimeException e) {
			}
			mySecondHit = (Element) myFirstHit.getParentElement().getChildren().get(1);
		}

		/*
		 * -------------------------------- vorhandene PPN als digitale oder
		 * analoge einsetzen --------------------------------
		 */
		String ppn = getElementFieldValue(myFirstHit, "003@", "0");
		ughhelp.replaceMetadatum(topstruct, inPrefs, "CatalogIDDigital", "");
		if (gattung.toLowerCase().startsWith("o"))
			ughhelp.replaceMetadatum(topstruct, inPrefs, "CatalogIDDigital", ppn);
		else
			ughhelp.replaceMetadatum(topstruct, inPrefs, "CatalogIDSource", ppn);

		/*
		 * -------------------------------- wenn es ein multivolume ist, dann
		 * auch die PPN prüfen --------------------------------
		 */
		if (topstructChild != null && mySecondHit != null) {
			String secondHitppn = getElementFieldValue(mySecondHit, "003@", "0");
			ughhelp.replaceMetadatum(topstructChild, inPrefs, "CatalogIDDigital", "");
			if (gattung.toLowerCase().startsWith("o"))
				ughhelp.replaceMetadatum(topstructChild, inPrefs, "CatalogIDDigital", secondHitppn);
			else
				ughhelp.replaceMetadatum(topstructChild, inPrefs, "CatalogIDSource", secondHitppn);
		}

		/*
		 * -------------------------------- den Main-Title bereinigen
		 * --------------------------------
		 */
		String myTitle = getElementFieldValue(myFirstHit, "021A", "a");
		/*
		 * wenn der Fulltittle nicht in dem Element stand, dann an anderer
		 * Stelle nachsehen (vor allem bei Contained-Work)
		 */
		if (myTitle == null || myTitle.length() == 0)
			myTitle = getElementFieldValue(myFirstHit, "021B", "a");
		ughhelp.replaceMetadatum(topstruct, inPrefs, "TitleDocMain", myTitle.replaceAll("@", ""));

		/*
		 * -------------------------------- Sorting-Titel mit
		 * Umlaut-Konvertierung --------------------------------
		 */
		if (myTitle.indexOf("@") != -1)
			myTitle = myTitle.substring(myTitle.indexOf("@") + 1);
		ughhelp.replaceMetadatum(topstruct, inPrefs, "TitleDocMainShort", myTitle);

		/*
		 * -------------------------------- bei multivolumes den Main-Title
		 * bereinigen --------------------------------
		 */
		if (topstructChild != null && mySecondHit != null) {
			String fulltitleMulti = getElementFieldValue(mySecondHit, "021A", "a").replaceAll("@", "");
			ughhelp.replaceMetadatum(topstructChild, inPrefs, "TitleDocMain", fulltitleMulti);
		}

		/*
		 * -------------------------------- bei multivolumes den Sorting-Titel
		 * mit Umlaut-Konvertierung --------------------------------
		 */
		if (topstructChild != null && mySecondHit != null) {
			String sortingTitleMulti = getElementFieldValue(mySecondHit, "021A", "a");
			if (sortingTitleMulti.indexOf("@") != -1)
				sortingTitleMulti = sortingTitleMulti.substring(sortingTitleMulti.indexOf("@") + 1);
			ughhelp.replaceMetadatum(topstructChild, inPrefs, "TitleDocMainShort", sortingTitleMulti);
			// sortingTitle = sortingTitleMulti;
		}

		/*
		 * -------------------------------- Sprachen - Konvertierung auf zwei
		 * Stellen --------------------------------
		 */
		String sprache = getElementFieldValue(myFirstHit, "010@", "a");
		sprache = ughhelp.convertLanguage(sprache);
		ughhelp.replaceMetadatum(topstruct, inPrefs, "DocLanguage", sprache);

		/*
		 * -------------------------------- bei multivolumes die Sprachen -
		 * Konvertierung auf zwei Stellen --------------------------------
		 */
		if (topstructChild != null && mySecondHit != null) {
			String spracheMulti = getElementFieldValue(mySecondHit, "010@", "a");
			spracheMulti = ughhelp.convertLanguage(spracheMulti);
			ughhelp.replaceMetadatum(topstructChild, inPrefs, "DocLanguage", spracheMulti);
		}

		/*
		 * -------------------------------- ISSN
		 * --------------------------------
		 */
		String issn = getElementFieldValue(myFirstHit, "005A", "0");
		ughhelp.replaceMetadatum(topstruct, inPrefs, "ISSN", issn);

		/*
		 * -------------------------------- Copyright
		 * --------------------------------
		 */
		String copyright = getElementFieldValue(myFirstHit, "037I", "a");
		ughhelp.replaceMetadatum(boundbook, inPrefs, "copyrightimageset", copyright);

		/*
		 * -------------------------------- Format
		 * --------------------------------
		 */
		String format = getElementFieldValue(myFirstHit, "034I", "a");
		ughhelp.replaceMetadatum(boundbook, inPrefs, "FormatSourcePrint", format);

		/*
		 * -------------------------------- Umfang
		 * --------------------------------
		 */
		String umfang = getElementFieldValue(myFirstHit, "034D", "a");
		ughhelp.replaceMetadatum(topstruct, inPrefs, "SizeSourcePrint", umfang);

		/*
		 * -------------------------------- Signatur
		 * --------------------------------
		 */
		String sig = getElementFieldValue(myFirstHit, "209A", "c");
		if (sig.length() > 0)
			sig = "<" + sig + ">";
		sig += getElementFieldValue(myFirstHit, "209A", "f") + " ";
		sig += getElementFieldValue(myFirstHit, "209A", "a");
		ughhelp.replaceMetadatum(boundbook, inPrefs, "shelfmarksource", sig.trim());
		if (sig.trim().length() == 0) {
			myLogger.debug("Signatur part 1: " + sig);
			myLogger.debug(myFirstHit.getChildren());
			sig = getElementFieldValue(myFirstHit, "209A/01", "c");
			if (sig.length() > 0)
				sig = "<" + sig + ">";
			sig += getElementFieldValue(myFirstHit, "209A/01", "f") + " ";
			sig += getElementFieldValue(myFirstHit, "209A/01", "a");
			if (mySecondHit != null) {
				sig += getElementFieldValue(mySecondHit, "209A", "f") + " ";
				sig += getElementFieldValue(mySecondHit, "209A", "a");
			}
			ughhelp.replaceMetadatum(boundbook, inPrefs, "shelfmarksource", sig.trim());
		}
		myLogger.debug("Signatur full: " + sig);

		/*
		 * -------------------------------- Ats Tsl Vorbereitung
		 * --------------------------------
		 */
		myTitle = myTitle.toLowerCase();
		myTitle = myTitle.replaceAll("&", "");

		/*
		 * -------------------------------- bei nicht-Zeitschriften Ats
		 * berechnen --------------------------------
		 */
		// if (!gattung.startsWith("ab") && !gattung.startsWith("ob")) {
		String autor = getElementFieldValue(myFirstHit, "028A", "a").toLowerCase();
		if (autor == null || autor.equals("")) {
			autor = getElementFieldValue(myFirstHit, "028A", "8").toLowerCase();
		}
		atstsl = createAtstsl(myTitle, autor);

		/*
		 * -------------------------------- bei Zeitschriften noch ein
		 * PeriodicalVolume als Child einfügen --------------------------------
		 */
		// if (isPeriodical()) {
		if (getOpacDocType().isPeriodical()) {
			try {
				DocStructType dstV = inPrefs.getDocStrctTypeByName("PeriodicalVolume");
				DocStruct dsvolume = inDigDoc.createDocStruct(dstV);
				topstruct.addChild(dsvolume);
			} catch (TypeNotAllowedForParentException e) {
				e.printStackTrace();
			} catch (TypeNotAllowedAsChildException e) {
				e.printStackTrace();
			}
		}

	}

	/**
	 * den Ats erzeugen und zurückgeben
	 * ================================================================
	 */
	public String createAtstsl(String myTitle, String autor) {
		String myAtsTsl = "";
		if (autor != null && !autor.equals("")) {
			/* autor */
			if (autor.length() > 4)
				myAtsTsl = autor.substring(0, 4);
			else
				myAtsTsl = autor;
			/* titel */

			if (myTitle.length() > 4)
				myAtsTsl += myTitle.substring(0, 4);
			else
				myAtsTsl += myTitle;
		}

		/*
		 * -------------------------------- bei Zeitschriften Tsl berechnen
		 * --------------------------------
		 */
		// if (gattung.startsWith("ab") || gattung.startsWith("ob")) {
		if (autor == null || autor.equals("")) {
			myAtsTsl = "";
			StringTokenizer tokenizer = new StringTokenizer(myTitle);
			int counter = 1;
			while (tokenizer.hasMoreTokens()) {
				String tok = tokenizer.nextToken();
				if (counter == 1) {
					if (tok.length() > 4)
						myAtsTsl += tok.substring(0, 4);
					else
						myAtsTsl += tok;
				}
				if (counter == 2 || counter == 3) {
					if (tok.length() > 2)
						myAtsTsl += tok.substring(0, 2);
					else
						myAtsTsl += tok;
				}
				if (counter == 4) {
					if (tok.length() > 1)
						myAtsTsl += tok.substring(0, 1);
					else
						myAtsTsl += tok;
				}
				counter++;
			}
		}
		/* im ATS-TSL die Umlaute ersetzen */
		if (FacesContext.getCurrentInstance() != null) {
			myAtsTsl = new UghHelper().convertUmlaut(myAtsTsl);
		}
		myAtsTsl = myAtsTsl.replaceAll("[\\W]", "");
		return myAtsTsl;
	}

	@SuppressWarnings("unchecked")
	private Element getElementFromChildren(Element inHit, String inTagName) {
		for (Iterator<Element> iter2 = inHit.getChildren().iterator(); iter2.hasNext();) {
			Element myElement = (Element) iter2.next();
			String feldname = myElement.getAttributeValue("tag");
			// System.out.println(feldname);
			/*
			 * wenn es das gesuchte Feld ist, dann den Wert mit dem passenden
			 * Attribut zurückgeben
			 */
			if (feldname.equals(inTagName))
				return myElement;
		}
		return null;
	}

	/**
	 * rekursives Kopieren von Elementen, weil das Einfügen eines Elements an
	 * einen anderen Knoten mit dem Fehler abbricht, dass das einzufügende
	 * Element bereits einen Parent hat
	 * ================================================================
	 */
	@SuppressWarnings("unchecked")
	private Element getCopyFromJdomElement(Element inHit) {
		Element myElement = new Element(inHit.getName());
		myElement.setText(inHit.getText());
		/* jetzt auch alle Attribute übernehmen */
		if (inHit.getAttributes() != null) {
			for (Iterator<Attribute> iter = inHit.getAttributes().iterator(); iter.hasNext();) {
				Attribute att = (Attribute) iter.next();
				myElement.getAttributes().add(new Attribute(att.getName(), att.getValue()));
			}
		}
		/* jetzt auch alle Children übernehmen */
		if (inHit.getChildren() != null) {

			for (Iterator<Element> iter = inHit.getChildren().iterator(); iter.hasNext();) {
				Element ele = (Element) iter.next();
				myElement.addContent(getCopyFromJdomElement(ele));
			}
		}
		return myElement;
	}

	@SuppressWarnings("unchecked")
	private String getElementFieldValue(Element myFirstHit, String inFieldName, String inAttributeName) {

		for (Iterator<Element> iter2 = myFirstHit.getChildren().iterator(); iter2.hasNext();) {
			Element myElement = (Element) iter2.next();
			String feldname = myElement.getAttributeValue("tag");
			/*
			 * wenn es das gesuchte Feld ist, dann den Wert mit dem passenden
			 * Attribut zurückgeben
			 */
			if (feldname.equals(inFieldName))
				return getFieldValue(myElement, inAttributeName);
		}
		return "";
	}

	@SuppressWarnings("unchecked")
	private String getFieldValue(Element inElement, String attributeValue) {
		String rueckgabe = "";

		for (Iterator<Element> iter = inElement.getChildren().iterator(); iter.hasNext();) {
			Element subElement = (Element) iter.next();
			if (subElement.getAttributeValue("code").equals(attributeValue))
				rueckgabe = subElement.getValue();
		}
		return rueckgabe;
	}

	public String getAtstsl() {
		return atstsl;
	}

	/*
	 * #####################################################
	 * ##################################################### ## ##
	 * Publikationstypen aus der Konfiguration auslesen ##
	 * #####################################################
	 * ####################################################
	 */

	// public boolean isMonograph() {
	// if (gattung != null && config.getParameter("docTypeMonograph",
	// "").contains(gattung.substring(0, 2)))
	// return true;
	// else
	// return false;
	// }
	// public boolean isPeriodical() {
	// if (gattung != null && config.getParameter("docTypePeriodical",
	// "").contains(gattung.substring(0, 2)))
	// return true;
	// else
	// return false;
	// }
	//
	// public boolean isMultivolume() {
	// if (gattung != null && config.getParameter("docTypeMultivolume",
	// "").contains(gattung.substring(0, 2)))
	// return true;
	// else
	// return false;
	// }
	//
	// public boolean isContainedWork() {
	// if (gattung != null
	// && config.getParameter("docTypeContainedWork",
	// "").contains(gattung.substring(0, 2)))
	// return true;
	// else
	// return false;
	// }
	public ConfigOpacDoctype getOpacDocType() {
		try {
			ConfigOpac co = new ConfigOpac();
			ConfigOpacDoctype cod = co.getDoctypeByMapping(gattung.substring(0, 2), coc.getTitle());
			if (cod == null) {
				Helper.setFehlerMeldung("Unbekannte Gattung: ", gattung);
				cod = new ConfigOpac().getAllDoctypes().get(0);
				gattung = cod.getMappings().get(0);
				Helper.setFehlerMeldung("changed docttype: ", gattung + " - " + cod.getTitle());
			}
			return cod;
		} catch (IOException e) {
			myLogger.error("OpacDoctype unknown", e);
			Helper.setFehlerMeldung("OpacDoctype unknown", e);
			return null;
		}
	}
}