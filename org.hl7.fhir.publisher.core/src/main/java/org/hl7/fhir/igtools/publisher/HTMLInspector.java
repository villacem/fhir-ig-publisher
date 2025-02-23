package org.hl7.fhir.igtools.publisher;

/*-
 * #%L
 * org.hl7.fhir.publisher.core
 * %%
 * Copyright (C) 2014 - 2019 Health Level 7
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.igtools.publisher.SpecMapManager.SpecialPackageType;
import org.hl7.fhir.r5.context.IWorkerContext.ILoggingService;
import org.hl7.fhir.r5.context.IWorkerContext.ILoggingService.LogCategory;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageHacker;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.utilities.xhtml.XhtmlNode.Location;
import org.hl7.fhir.utilities.xhtml.XhtmlParser;

import javax.annotation.Nonnull;


//import org.owasp.html.Handler;
//import org.owasp.html.HtmlChangeListener;
//import org.owasp.html.HtmlPolicyBuilder;
//import org.owasp.html.HtmlSanitizer;
//import org.owasp.html.HtmlStreamEventReceiver;
//import org.owasp.html.HtmlStreamRenderer;
//import org.owasp.html.PolicyFactory;
//import org.owasp.html.Sanitizers;

public class HTMLInspector {

  
  public enum NodeChangeType {
    NONE, SELF, CHILD
  }

  public class HtmlChangeListenerContext {

    private List<ValidationMessage> messages;
    private String source;

    public HtmlChangeListenerContext(List<ValidationMessage> messages, String source) {
      this.messages = messages;
      this.source = source;
    }
  }

//  public class HtmlSanitizerObserver implements HtmlChangeListener<HtmlChangeListenerContext> {
//
//    @Override
//    public void discardedAttributes(HtmlChangeListenerContext ctxt, String elementName, String... attributeNames) {
//      ctxt.messages.add(new ValidationMessage(Source.Publisher, IssueType.STRUCTURE, ctxt.source, "the element "+elementName+" attributes failed security testing", IssueSeverity.ERROR));
//    }
//
//    @Override
//    public void discardedTag(HtmlChangeListenerContext ctxt, String elementName) {
//      ctxt.messages.add(new ValidationMessage(Source.Publisher, IssueType.STRUCTURE, ctxt.source, "the element "+elementName+" failed security testing", IssueSeverity.ERROR));
//    }
//  }

  public class StringPair {
    private String source;
    private String link;
    private String text;
    public StringPair(String source, String link, String text) {
      super();
      this.source = source;
      this.link = link;
      this.text = text;
    }
  }

  public class LoadedFile {
    private String filename;
    private long lastModified;
    private int iteration;
    private Set<String> targets = new HashSet<String>();
    private Boolean hl7State;
    private boolean exempt;
    private String path;
    private boolean hasXhtml;
    private int id = 0;

    public LoadedFile(String filename, String path, long lastModified, int iteration, Boolean hl7State, boolean exempt, boolean hasXhtml) {
      this.filename = filename;
      this.lastModified = lastModified;
      this.iteration = iteration;
      this.hl7State = hl7State;
      this.path = path;
      this.exempt = exempt;
      this.hasXhtml = hasXhtml;
    }

    public long getLastModified() {
      return lastModified;
    }

    public int getIteration() {
      return iteration;
    }

    public void setIteration(int iteration) {
      this.iteration = iteration;
    }

    public boolean isExempt() {
      return exempt;
    }

    public Set<String> getTargets() {
      return targets;
    }

    public String getFilename() {
      return filename;
    }

    public Boolean getHl7State() {
      return hl7State;
    }

    public boolean isHasXhtml() {
      return hasXhtml;
    }

    public String getNextId() {
      id++;
      return Integer.toString(id );
    }
    
  }

  private static final String RELEASE_HTML_MARKER = "<!--ReleaseHeader--><p id=\"publish-box\">Publish Box goes here</p><!--EndReleaseHeader-->";
  private static final String START_HTML_MARKER = "<!--ReleaseHeader--><p id=\"publish-box\">";
  private static final String END_HTML_MARKER = "</p><!--EndReleaseHeader-->";
  public static final String TRACK_PREFIX = "<!--$$";
  public static final String TRACK_SUFFIX = "$$-->";

  private boolean strict;
  private String rootFolder;
  private String altRootFolder;
  private List<SpecMapManager> specs;
  private Map<String, LoadedFile> cache = new HashMap<String, LoadedFile>();
  private int iteration = 0;
  private List<StringPair> otherlinks = new ArrayList<StringPair>();
  private int links;
  private List<String> manual = new ArrayList<String>(); // pages that will be provided manually when published, so allowed to be broken links
  private ILoggingService log;
  private boolean forHL7;
  private boolean requirePublishBox;
  private List<String> igs;
  private FilesystemPackageCacheManager pcm;
  private Map<String, SpecMapManager> otherSpecs = new HashMap<String, SpecMapManager>();
  private Map<String, String> specList = new HashMap<>();
  private List<String> errorPackages = new ArrayList<>();
  private String canonical;

  private String statusText;
  private List<String> exemptHtmlPatterns = new ArrayList<>();
  private boolean missingPublishBox;
  private List<String> missingPublishBoxList = new ArrayList<>();
  private Set<String> exceptions = new HashSet<>();
  private boolean referencesValidatorPack;
  private Map<String, List<String>> trackedFragments;
  private Set<String> foundFragments = new HashSet<>();

  public HTMLInspector(String rootFolder, List<SpecMapManager> specs, ILoggingService log, String canonical, String packageId, Map<String, List<String>> trackedFragments) {
    this.rootFolder = rootFolder.replace("/", File.separator);
    this.specs = specs;
    this.log = log;
    this.canonical = canonical;
    this.forHL7 = canonical.contains("hl7.org/fhir");
    this.trackedFragments = trackedFragments;
    requirePublishBox = Utilities.startsWithInList(packageId, "hl7."); 
  }

  public void setAltRootFolder(String altRootFolder) throws IOException {
    this.altRootFolder = Utilities.path(rootFolder, altRootFolder.replace("/", File.separator));
  }
  
  public List<ValidationMessage> check(String statusText) throws IOException {  
    this.statusText = statusText;
    iteration ++;

    List<ValidationMessage> messages = new ArrayList<ValidationMessage>();

    log.logDebugMessage(LogCategory.HTML, "CheckHTML: List files");
    // list new or updated files
    List<String> loadList = new ArrayList<>();
    listFiles(rootFolder, loadList);
    log.logMessage("found "+Integer.toString(loadList.size())+" files");

    checkGoneFiles();

    log.logDebugMessage(LogCategory.HTML, "Loading Files");
    // load files
    int i = 0;
    int c = loadList.size() / 40;
    for (String s : loadList) {
      loadFile(s, rootFolder, messages);
      if (i == c) {
        System.out.print(".");
        i = 0;
      }
      i++;
    }
    System.out.println();


    log.logDebugMessage(LogCategory.HTML, "Checking Files");
    links = 0;
    // check links
    boolean first = true;
    i = 0;
    c = cache.size() / 40;
    for (String s : sorted(cache.keySet())) {
      log.logDebugMessage(LogCategory.HTML, "Check "+s);
      LoadedFile lf = cache.get(s);

      if (lf.getHl7State() != null && !lf.getHl7State()) {
        boolean check = true;
        for (String pattern : exemptHtmlPatterns ) {
          if (lf.path.matches(pattern)) {
            check = false;
            break;
          }
        }
        if (check && !lf.isExempt()) {
          if (requirePublishBox) {
            messages.add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, s, "The html source does not contain the publish box" 
              + (first ? " "+RELEASE_HTML_MARKER+" (see note at http://wiki.hl7.org/index.php?title=FHIR_Implementation_Guide_Publishing_Requirements#HL7_HTML_Standards_considerations)" : ""), IssueSeverity.ERROR));
          } else if (first) {
            messages.add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, s, "The html source does not contain the publish box; this is recommended for publishing support",
                "The html source does not contain the publish box; this is recommended for publishing support  (see note at http://wiki.hl7.org/index.php?title=FHIR_Implementation_Guide_Publishing_Requirements#HL7_HTML_Standards_considerations). Note that this is mandatory for HL7 specifications, and on the ci-build, but in other cases it's still recommended (this is only reported once, but applies for all pages)", IssueSeverity.INFORMATION));            
            
          }
          missingPublishBox = true;
          missingPublishBoxList.add(s.substring(rootFolder.length()+1));
          first = false;
        }
      }
      checkFragmentIds(TextFile.fileToString(lf.filename));
      
      if (lf.isHasXhtml()) {
        XhtmlNode x = new XhtmlParser().setMustBeWellFormed(strict).parse(new FileInputStream(lf.filename), null);
        referencesValidatorPack = false;
        if (checkLinks(lf, s, "", x, null, messages, false) != NodeChangeType.NONE) { // returns true if changed
          saveFile(lf, x);
        }
        if (referencesValidatorPack) {
          if (lf.getHl7State() != null && lf.getHl7State()) {
            messages.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, s, "The html source references validator.pack which is deprecated. Change the IG to describe the use of the package system instead", IssueSeverity.ERROR));                      
          } else {
            messages.add(new ValidationMessage(Source.Publisher, IssueType.BUSINESSRULE, s, "The html source references validator.pack which is deprecated. Change the IG to describe the use of the package system instead", IssueSeverity.WARNING));                                  
          }
        }
      }
      if (i == c) {
        System.out.print(".");
        i = 0;
      }
      i++;
    }
    System.out.println();
 
    log.logDebugMessage(LogCategory.HTML, "Checking Other Links");
    // check other links:
    for (StringPair sp : otherlinks) {
      checkResolveLink(sp.source, null, null, sp.link, sp.text, messages, null);
    }
    
    log.logDebugMessage(LogCategory.HTML, "Done checking");
    
    for (String s : trackedFragments.keySet()) {
      if (!foundFragments.contains(s)) {
        if (trackedFragments.get(s).size() > 1) {
          messages.add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, s, "An HTML fragment from the set "+trackedFragments.get(s)+" is not included anywhere in the produced implementation guide",
              "An HTML fragment from the set "+trackedFragments.get(s)+" is not included anywhere in the produced implementation guide", IssueSeverity.WARNING));
        } else {
          messages.add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, s, "The HTML fragment '"+trackedFragments.get(s).get(0)+"' is not included anywhere in the produced implementation guide",
            "The HTML fragment '"+trackedFragments.get(s).get(0)+"' is not included anywhere in the produced implementation guide", IssueSeverity.WARNING));
        }
      }
    }
    return messages;
  }

  private void checkFragmentIds(String src) {
    int s = src.indexOf(TRACK_PREFIX);
    while (s > -1) {
      src = src.substring(s+TRACK_PREFIX.length());
      int e = src.indexOf(TRACK_SUFFIX);
      foundFragments.add(src.substring(0, e));
      s = src.indexOf(TRACK_PREFIX);
    }    
  }

  private List<String> sorted(Set<String> keys) {
    List<String> res = new ArrayList<>();
    res.addAll(keys);
    Collections.sort(res);
    return res;
  }

  private void saveFile(LoadedFile lf, XhtmlNode x) throws IOException {
    new File(lf.getFilename()).delete();
    FileOutputStream f = new FileOutputStream(lf.getFilename());
    new XhtmlComposer(XhtmlComposer.HTML).composeDocument(f, x);
    f.close();
  }

  private void checkGoneFiles() {
    List<String> td = new ArrayList<String>();
    for (String s : cache.keySet()) {
      LoadedFile lf = cache.get(s);
      if (lf.getIteration() != iteration)
        td.add(s);
    }
    for (String s : td)
      cache.remove(s);
  }

  private void listFiles(String folder, List<String> loadList) {
    for (File f : new File(folder).listFiles()) {
      if (!Utilities.startsWithInList(f.getAbsolutePath(), exceptions)) {
        if (f.isDirectory()) {
          listFiles(f.getAbsolutePath(), loadList);
        } else {
          LoadedFile lf = cache.get(f.getAbsolutePath());
          if (lf == null || lf.getLastModified() != f.lastModified())
            loadList.add(f.getAbsolutePath());
          else
            lf.setIteration(iteration);
        }
      }
    }
  }

  private void loadFile(String s, String base, List<ValidationMessage> messages) {
    log.logDebugMessage(LogCategory.HTML, "Load "+s);

    File f = new File(s);
    Boolean hl7State = null;
    XhtmlNode x = null;
    boolean htmlName = f.getName().endsWith(".html") || f.getName().endsWith(".xhtml");
    try {
      x = new XhtmlParser().setMustBeWellFormed(strict).parse(new FileInputStream(f), null);
      if (x.getElement("html")==null && !htmlName) {
        // We don't want resources being treated as HTML.  We'll check the HTML of the narrative in the page representation
        x = null;
      }
    } catch (FHIRFormatError | IOException e) {
      x = null;
      if (htmlName || !(e.getMessage().startsWith("Unable to Parse HTML - does not start with tag.") || e.getMessage().startsWith("Malformed XHTML"))) {
    	  messages.add(new ValidationMessage(Source.LinkChecker, IssueType.STRUCTURE, s, e.getMessage(), IssueSeverity.ERROR).setLocationLink(makeLocal(f.getAbsolutePath())));
      }
    }
    if (x != null) {
      String src;
      try {
        src = TextFile.fileToString(f);
        hl7State = src.contains(RELEASE_HTML_MARKER);
        if (hl7State) {
          src = src.replace(RELEASE_HTML_MARKER, START_HTML_MARKER + statusText+END_HTML_MARKER);
          TextFile.stringToFile(src, f, false);
        }
        x = new XhtmlParser().setMustBeWellFormed(strict).parse(new FileInputStream(f), null);
      } catch (Exception e1) {
        hl7State = false;
      }
    }
    LoadedFile lf = new LoadedFile(s, getPath(s, base), f.lastModified(), iteration, hl7State, findExemptionComment(x) || Utilities.existsInList(f.getName(), "searchform.html"), x != null);
    cache.put(s, lf);
    if (x != null) {
      checkHtmlStructure(s, x, messages);
      listTargets(x, lf.getTargets());
      if (forHL7 & !isRedirect(x)) {
        checkTemplatePoints(x, messages, s);
      }
    }
    
    // ok, now check for XSS safety:
    // this is presently disabled; it's not clear whether oWasp is worth trying out for the purpose we are seeking (XSS safety)
    
//    
//    HtmlPolicyBuilder pp = new HtmlPolicyBuilder();
//    pp
//      .allowStandardUrlProtocols().allowAttributes("title").globally() 
//      .allowElements("html", "head", "meta", "title", "body", "span", "link", "nav", "button")
//      .allowAttributes("xmlns", "xml:lang", "lang", "charset", "name", "content", "id", "class", "href", "rel", "sizes", "no-external", "target", "data-target", "data-toggle", "type", "colspan").globally();
//    
//    PolicyFactory policy = Sanitizers.FORMATTING.and(Sanitizers.LINKS).and(Sanitizers.BLOCKS).and(Sanitizers.IMAGES).and(Sanitizers.STYLES).and(Sanitizers.TABLES).and(pp.toFactory());
//    
//    String source;
//    try {
//      source = TextFile.fileToString(s);
//      HtmlChangeListenerContext ctxt = new HtmlChangeListenerContext(messages, s);
//      String sanitized = policy.sanitize(source, new HtmlSanitizerObserver(), ctxt);
//    } catch (IOException e) {
//      messages.add(new ValidationMessage(Source.Publisher, IssueType.STRUCTURE, s, "failed security testing: "+e.getMessage(), IssueSeverity.ERROR));
//    } 
  }

  private String getPath(String s, String base) {
    String t = s.substring(base.length()+1);
    return t.replace("\\", "/");
  }

  private boolean isRedirect(XhtmlNode x) {
    return !hasHTTPRedirect(x);
  }

  private boolean hasHTTPRedirect(XhtmlNode x) {
    if ("meta".equals(x.getName()) && x.hasAttribute("http-equiv"))
      return true;
    for (XhtmlNode c : x.getChildNodes())
      if (hasHTTPRedirect(c))
        return true;
    return false;
  }

  private void checkTemplatePoints(XhtmlNode x, List<ValidationMessage> messages, String s) {
    // look for a footer: a div tag with igtool=footer on it 
    XhtmlNode footer = findFooterDiv(x);
    if (footer == null && !findExemptionComment(x)) 
      messages.add(new ValidationMessage(Source.Publisher, IssueType.STRUCTURE, s, "The html must include a div with an attribute igtool=\"footer\" that marks the footer in the template", IssueSeverity.ERROR));
    else {
      // look in the footer for: .. nothing yet... 
    }
  }

  private boolean findExemptionComment(XhtmlNode x) {
    if (x == null) {
      return false;
    }
    for (XhtmlNode c : x.getChildNodes()) {
      if (c.getNodeType() == NodeType.Comment && x.getContent() != null && x.getContent().trim().equals("frameset content"))
        return true;
    }
    return false;
  }

  private XhtmlNode findFooterDiv(XhtmlNode x) {
    if (x.getNodeType() == NodeType.Element && "footer".equals(x.getAttribute("igtool")))
      return x;
    for (XhtmlNode c : x.getChildNodes()) {
      XhtmlNode n = findFooterDiv(c);
      if (n != null)
        return n;
    }
    return null;
  }

  private boolean findStatusBarComment(XhtmlNode x) {
    if (x.getNodeType() == NodeType.Comment && "status-bar".equals(x.getContent().trim()))
      return true;
    for (XhtmlNode c : x.getChildNodes()) {
      if (findStatusBarComment(c))
        return true;
    }
    return false;
  }

  private void checkHtmlStructure(String s, XhtmlNode x, List<ValidationMessage> messages) {
    if (x.getNodeType() == NodeType.Document)
      x = x.getFirstElement();
    if (!"html".equals(x.getName()) && !"div".equals(x.getName()))
      messages.add(new ValidationMessage(Source.Publisher, IssueType.STRUCTURE, s, "Root node must be 'html' or 'div', but is "+x.getName(), IssueSeverity.ERROR));
    // We support div as well because with HTML 5, referenced files might just start with <div>
    // todo: check secure?
    
  }

  private void listTargets(XhtmlNode x, Set<String> targets) {
    if ("a".equals(x.getName()) && x.hasAttribute("name"))
      targets.add(x.getAttribute("name"));
    if (x.hasAttribute("id"))
      targets.add(x.getAttribute("id"));
    if (Utilities.existsInList(x.getName(), "h1", "h2", "h3", "h4", "h5", "h6")) {
      if (x.allText() != null) {
        targets.add(urlify(x.allText()));
      }
    }
    for (XhtmlNode c : x.getChildNodes())
      listTargets(c, targets);
  }

  private NodeChangeType checkLinks(LoadedFile lf, String s, String path, XhtmlNode x, String uuid, List<ValidationMessage> messages, boolean inPre) throws IOException {
    boolean changed = false;
    if (x.getName() != null) {
      path = path + "/"+ x.getName();
    } else {
      if (x.getContent() != null && x.getContent().contains("validator.pack")) {
        referencesValidatorPack = true;
      }
    }
    if ("title".equals(x.getName()) && Utilities.noString(x.allText())) {
      x.addText("?html-link?");
    }
    if ("a".equals(x.getName()) && x.hasAttribute("href")) {
      changed = checkResolveLink(s, x.getLocation(), path, x.getAttribute("href"), x.allText(), messages, uuid);
    }
    if ("img".equals(x.getName()) && x.hasAttribute("src")) {
      changed = checkResolveImageLink(s, x.getLocation(), path, x.getAttribute("src"), messages, uuid) || changed;
    }
    if ("link".equals(x.getName())) {
      changed = checkLinkElement(s, x.getLocation(), path, x.getAttribute("href"), messages, uuid) || changed;
    }
    if ("script".equals(x.getName())) {
      checkScriptElement(s, x.getLocation(), path, x, messages);
    } 
    String nuid = genID(lf);
    boolean nchanged = false;
    boolean nSelfChanged = false;
    for (XhtmlNode c : x.getChildNodes()) { 
      NodeChangeType ct = checkLinks(lf, s, path, c, nuid, messages, inPre || "pre".equals(x.getName()));
      if (ct == NodeChangeType.SELF) {
        nSelfChanged = true;
        nchanged = true;
      } else if (ct == NodeChangeType.CHILD) {
        nchanged = true;
      }      
    }
    if (nSelfChanged) {
      XhtmlNode a = new XhtmlNode(NodeType.Element);
      a.setName("a").setAttribute("name", nuid).addText("\u200B");
      x.getChildNodes().add(0, a);
    } 
    if (changed)
      return NodeChangeType.SELF;
    else if (nchanged)
      return NodeChangeType.CHILD;
    else
      return NodeChangeType.NONE;
  }

  public String genID(LoadedFile lf) {
    return "l"+lf.getNextId(); // UUID.randomUUID().toString().toLowerCase();
  }

  private void checkScriptElement(String filename, Location loc, String path, XhtmlNode x, List<ValidationMessage> messages) {
    String src = x.getAttribute("src");
    if (!Utilities.noString(src) && Utilities.isAbsoluteUrl(src) && !Utilities.existsInList(src, 
        "http://hl7.org/fhir/history-cm.js", "http://hl7.org/fhir/assets-hist/js/jquery.js") && !src.contains("googletagmanager.com"))
      messages.add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, filename+(loc == null ? "" : " at "+loc.toString()), "The <script> src '"+src+"' is llegal", IssueSeverity.FATAL));    
  }

  private boolean checkLinkElement(String filename, Location loc, String path, String href, List<ValidationMessage> messages, String uuid) {
    if (Utilities.isAbsoluteUrl(href) && !href.startsWith("http://hl7.org/") && !href.startsWith("http://cql.hl7.org/")) {
      messages.add(new ValidationMessage(Source.Publisher, IssueType.NOTFOUND, filename+(loc == null ? "" : " at "+loc.toString()), "The <link> href '"+href+"' is llegal", IssueSeverity.FATAL).setLocationLink(uuid == null ? null : filename+"#"+uuid));
      return true;        
    } else
      return false;
  }

  private boolean checkResolveLink(String filename, Location loc, String path, String ref, String text, List<ValidationMessage> messages, String uuid) throws IOException {
    links++;
    String rref = Utilities.URLDecode(ref);
    if ((rref.startsWith("http:") || rref.startsWith("https:") ) && (rref.endsWith(".sch") || rref.endsWith(".xsd") || rref.endsWith(".shex"))) { // work around for the fact that spec.internals does not track all these minor things 
      rref = Utilities.changeFileExt(ref, ".html");
    }
    if (rref.startsWith("./")) {
      rref = rref.substring(2);
    }
    if (rref.endsWith("/")) {
      rref = rref.substring(0, rref.length()-1);
    }
    
    if (rref.contains("validator.pack")) {
      referencesValidatorPack = true;
    }
    if (ref.startsWith("data:")) {
      return true;
    }
    String tgtList = "";
    boolean resolved = Utilities.existsInList(ref, "qa.html", "http://hl7.org/fhir", "http://hl7.org", "http://www.hl7.org", "http://hl7.org/fhir/search.cfm") || 
        ref.startsWith("http://gforge.hl7.org/gf/project/fhir/tracker/") || ref.startsWith("mailto:") || ref.startsWith("javascript:");
    if (!resolved && forHL7)
      resolved = Utilities.pathURL(canonical, "history.html").equals(ref) || ref.equals("searchform.html"); 
    if (!resolved )
      resolved = filename.contains("searchform.html") && ref.equals("history.html"); 
    if (!resolved)
      resolved = manual.contains(rref);
    if (!resolved) {
      resolved = rref.startsWith("http://build.fhir.org/ig/FHIR/fhir-tools-ig") || rref.startsWith("http://build.fhir.org/ig/FHIR/ig-guidance"); // always allowed to refer to tooling or IG Guidance IG build location
    }
    if (!resolved && specs != null){
      for (SpecMapManager spec : specs) {
        if (!resolved && spec.getBase() != null) {
          resolved = resolved || spec.getBase().equals(rref) || (spec.getBase()).equals(rref+"/") || (spec.getBase()+"/").equals(rref)|| spec.hasTarget(rref) || 
            Utilities.existsInList(rref, Utilities.pathURL(spec.getBase(), "definitions.json.zip"), 
                Utilities.pathURL(spec.getBase(), "full-ig.zip"), Utilities.pathURL(spec.getBase(), "definitions.xml.zip"), 
                Utilities.pathURL(spec.getBase(), "package.tgz"), Utilities.pathURL(spec.getBase(), "history.html"));
        }
        if (!resolved && spec.getBase2() != null) {
          resolved = spec.getBase2().equals(rref) || (spec.getBase2()).equals(rref+"/") || 
              Utilities.existsInList(rref, Utilities.pathURL(spec.getBase2(), "definitions.json.zip"), Utilities.pathURL(spec.getBase2(), "definitions.xml.zip"), Utilities.pathURL(spec.getBase2(), "package.tgz"), Utilities.pathURL(spec.getBase2(), "full-ig.zip")); 
        }
      }
    }
    
    if (!resolved) {
      if (Utilities.isAbsoluteFileName(ref)) {
        if (new File(ref).exists()) {
          resolved = true;
        }
      } else if (!resolved && !Utilities.isAbsoluteUrl(ref) && !rref.startsWith("#")) {
        String fref =  buildRef(Utilities.getDirectoryForFile(filename), ref);
        if (fref.equals(Utilities.path(rootFolder, "qa.html"))) {
          resolved = true;
        }
      }
    }
    // special case end-points that are always valid:
     if (!resolved)
      resolved = Utilities.existsInList(ref, "http://hl7.org/fhir/fhir-spec.zip", "http://hl7.org/fhir/R4/fhir-spec.zip", "http://hl7.org/fhir/STU3/fhir-spec.zip", "http://hl7.org/fhir/DSTU2/fhir-spec.zip", 
          "http://hl7.org/fhir-issues", "http://hl7.org/registry") || 
          matchesTarget(ref, "http://hl7.org", "http://hl7.org/fhir/DSTU2", "http://hl7.org/fhir/STU3", "http://hl7.org/fhir/R4", "http://hl7.org/fhir/smart-app-launch", "http://hl7.org/fhir/validator");

     if (!resolved) { // updated table documentation
       if (ref.startsWith("https://build.fhir.org/ig/FHIR/ig-guidance/readingIgs.html")) {
         resolved = true;
       }
     }
     // a local file may have been created by some poorly tracked process, so we'll consider that as a possible
     if (!resolved && !Utilities.isAbsoluteUrl(rref) && !rref.contains("..")) { // .. is security check. Maybe there's some ways it could be valid, but we're not interested for now
       String fname = buildRef(new File(filename).getParent(), rref);
       if (new File(fname).exists()) {
         resolved = true;
       }
     }
     
     // external terminology resources 
     if (!resolved) {
       resolved = Utilities.startsWithInList(ref, "http://cts.nlm.nih.gov/fhir"); 
     }
    
    if (!resolved) {
      if (rref.startsWith("http://") || rref.startsWith("https://") || rref.startsWith("ftp://") || rref.startsWith("tel:")) {
        resolved = true;
        if (specs != null) {
          for (SpecMapManager spec : specs) {
            if (spec.getSpecial() != SpecialPackageType.Examples && spec.getBase() != null && rref.startsWith(spec.getBase())) {
              resolved = false;
            }
          }
        }
      } else if (!Utilities.isAbsoluteFileName(rref)) { 
        String page = rref;
        String name = null;
        if (page.startsWith("#")) {
          name = page.substring(1);
          page = filename;
        } else if (page.contains("#")) {
          name = page.substring(page.indexOf("#")+1);
          if (altRootFolder != null && filename.startsWith(altRootFolder))
            page = Utilities.path(altRootFolder, page.substring(0, page.indexOf("#")).replace("/", File.separator));
          else
            page = Utilities.path(rootFolder, page.substring(0, page.indexOf("#")).replace("/", File.separator));
        } else {
          String folder = Utilities.getDirectoryForFile(filename);
          page = Utilities.path(folder == null ? (altRootFolder != null && filename.startsWith(altRootFolder) ? altRootFolder : rootFolder) : folder, page.replace("/", File.separator));
        }
        LoadedFile f = cache.get(page);
        if (f != null) {
          if (Utilities.noString(name))
            resolved = true;
          else { 
            resolved = f.targets.contains(name);
            tgtList = " (valid targets: "+(f.targets.size() > 40 ? Integer.toString(f.targets.size())+" targets"  :  f.targets.toString())+")";
            for (String s : f.targets) {
              if (s.equalsIgnoreCase(name)) {
                tgtList = (" - case is wrong ('"+s+"')");
              }
            }
          }
        }
      }
    }
    if (resolved) {
      return false;
    } else {
      if (text == null)
        text = "";
      messages.add(new ValidationMessage(Source.LinkChecker, IssueType.NOTFOUND, filename+(path == null ? "" : "#"+path+(loc == null ? "" : " at "+loc.toString())), "The link '"+ref+"' for \""+text.replaceAll("[\\s\\n]+", " ").trim()+"\" cannot be resolved"+tgtList, IssueSeverity.ERROR).setLocationLink(uuid == null ? null : makeLocal(filename)+"#"+uuid));
      return true;
    } 
  }

  @Nonnull
  private String buildRef(String refParentPath, String ref) throws IOException {
    //FIXME This logic should be in Utilities.path
    // Utilities path will try to assemble a filesystem path,
    // and this will fail in Windows if it contains ':' characters.
    return Utilities.path(refParentPath) + File.separator + ref;
  }

  private boolean matchesTarget(String ref, String... url) {
    for (String s : url) {
      if (ref.equals(s))
        return true;
      if (ref.equals(s+"/"))
        return true;
      if (ref.equals(s+"/index.html"))
        return true;
    }
    return false;
  }

  private SpecMapManager loadSpecMap(String id, String ver, String url) throws IOException {
    NpmPackage pi = pcm.loadPackageFromCacheOnly(id, ver);
    if (pi == null) {
      System.out.println("Fetch "+id+" package from "+url);
      URL url1 = new URL(Utilities.pathURL(url, "package.tgz")+"?nocache=" + System.currentTimeMillis());
      URLConnection c = url1.openConnection();
      InputStream src = c.getInputStream();
      pi = pcm.addPackageToCache(id, ver, src, url);
    }    
    SpecMapManager sm = new SpecMapManager(TextFile.streamToBytes(pi.load("other", "spec.internals")), pi.getNpm().getJsonObject("dependencies").asString("hl7.fhir.core"));
    sm.setBase(PackageHacker.fixPackageUrl(url));
    return sm;
  }

  private String makeLocal(String filename) {
    if (filename.startsWith(rootFolder))
      return filename.substring(rootFolder.length()+1);
    return filename;
  }

  private boolean checkResolveImageLink(String filename, Location loc, String path, String ref, List<ValidationMessage> messages, String uuid) throws IOException {
    links++;
    String tgtList = "";
    boolean resolved = Utilities.existsInList(ref);
    if (ref.startsWith("data:"))
      resolved = true;
    if (ref.startsWith("./")) {
      ref = ref.substring(2);
    }
    if (!resolved)
      resolved = manual.contains(ref);
    if (!resolved && specs != null){
      for (SpecMapManager spec : specs) {
        resolved = resolved || (spec.getBase() != null && spec.hasImage(ref)); 
      }
    }
    if (!resolved) {
      ;resolved = Utilities.existsInList(ref, "http://hl7.org/fhir/assets-hist/images/fhir-logo-www.png", "http://hl7.org/fhir/assets-hist/images/hl7-logo-n.png"); 
    }
    if (!resolved) {
      if (ref.startsWith("http://") || ref.startsWith("https://")) {
        resolved = true;
        if (specs != null) {
          for (SpecMapManager spec : specs) {
            if (ref.startsWith(spec.getBase()))
              resolved = false;
          }
        }
      } else if (!ref.contains("#")) { 
        String page = Utilities.path(Utilities.getDirectoryForFile(filename), ref.replace("/", File.separator));
        LoadedFile f = cache.get(page);
        resolved = f != null;
      }
    }
      
    if (resolved)
      return false;
    else {
      messages.add(new ValidationMessage(Source.LinkChecker, IssueType.NOTFOUND, filename+(path == null ? "" : "#"+path+(loc == null ? "" : " at "+loc.toString())), "The image source '"+ref+"' cannot be resolved"+tgtList, IssueSeverity.ERROR).setLocationLink(uuid == null ? null : filename+"#"+uuid));
      return true;
    } 
  }

  public void addLinkToCheck(String source, String link, String text) {
    otherlinks.add(new StringPair(source, link, text));
    
  }

  public int total() {
    return cache.size();
  }

  public int links() {
    return links;
  }

  public static void main(String[] args) throws Exception {
    HTMLInspector inspector = new HTMLInspector(args[0], null, null, "http://hl7.org/fhir/us/core", "hl7.fhir.us.core", new HashMap<>());
    inspector.setStrict(false);
    List<ValidationMessage> linkmsgs = inspector.check("test text");
    int bl = 0;
    int lf = 0;
    for (ValidationMessage m : linkmsgs) {
      if ((m.getLevel() == IssueSeverity.ERROR) || (m.getLevel() == IssueSeverity.FATAL)) {
        if (m.getType() == IssueType.NOTFOUND)
          bl++;
        else
          lf++;
      } 
    }
    System.out.println("  ... "+Integer.toString(inspector.total())+" html "+checkPlural("file", inspector.total())+", "+Integer.toString(lf)+" "+checkPlural("page", lf)+" invalid xhtml ("+(inspector.total() == 0 ? "" : Integer.toString((lf*100)/inspector.total())+"%)"));
    System.out.println("  ... "+Integer.toString(inspector.links())+" "+checkPlural("link", inspector.links())+", "+Integer.toString(bl)+" broken "+checkPlural("link", lf)+" ("+(inspector.links() == 0 ? "" : Integer.toString((bl*100)/inspector.links())+"%)"));
    
    System.out.println("");
    
    for (ValidationMessage m : linkmsgs) 
      if ((m.getLevel() == IssueSeverity.ERROR) || (m.getLevel() == IssueSeverity.FATAL)) 
        System.out.println(m.summary());
  }

  private static String checkPlural(String word, int c) {
    return c == 1 ? word : Utilities.pluralizeMe(word);
  }

  public List<String> getManual() {
    return manual;
  }

  public void setManual(List<String> manual) {
    this.manual = manual;
  }

  public boolean isStrict() {
    return strict;
  }

  public void setStrict(boolean strict) {
    this.strict = strict;
  }

  public  List<SpecMapManager> getSpecMaps() {
    return specs;
  }

  public List<String> getIgs() {
    return igs;
  }

  public void setPcm(FilesystemPackageCacheManager pcm) {
    this.pcm = pcm; 
  }

  public List<String> getExemptHtmlPatterns() {
    return exemptHtmlPatterns;
  }

  public boolean isMissingPublishBox() {
    return missingPublishBox;
  }

  public Set<String> getExceptions() {
    return exceptions;
  }

  public String getMissingPublishboxSummary() {
    CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder();
    for (int i = 0; i < missingPublishBoxList.size() && i < 10; i++) {
      b.append(missingPublishBoxList.get(i));
    }    
    if (missingPublishBoxList.size() > 10) {
      return b.toString()+" + "+Integer.toString(missingPublishBoxList.size()-10)+" other files";
    } else {
      return b.toString();
    }
  }


  // adapted from anchor.min, which is used to generate these things on the flt 
  private String urlify(String a) {
    String repl = "-& +$,:;=?@\"#{}()[]|^~[`%!'<>].*";
    String elim = "/\\";
    StringBuilder b = new StringBuilder();
    boolean nextDash = false;
    for (char ch : a.toCharArray()) {
      if (elim.indexOf(ch) == -1) {
        if (repl.indexOf(ch) == -1) {
          if (nextDash) {
            b.append("-");
          }
          nextDash = false;
          b.append(Character.toLowerCase(ch));
        } else {
          nextDash = true;
        }
      }
    }
    String s = b.toString().trim();
    return s;
  }
  
}
