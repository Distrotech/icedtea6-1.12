// Copyright (C) 2001-2003 Jon A. Maxwell (JAM)
// Copyright (C) 2009 Red Hat, Inc.
// 
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
// 
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
// 
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.


package net.sourceforge.jnlp;

import java.io.*;
import java.net.*;
import java.util.*;
//import javax.xml.parsers.*; // commented to use right Node
//import org.w3c.dom.*;       // class for using Tiny XML | NanoXML
//import org.xml.sax.*;
//import gd.xml.tiny.*;
import net.sourceforge.jnlp.runtime.JNLPRuntime;
import net.sourceforge.nanoxml.*;


/**
 * Contains methods to parse an XML document into a JNLPFile.
 * Implements JNLP specification version 1.0.
 *
 * @author <a href="mailto:jmaxwell@users.sourceforge.net">Jon A. Maxwell (JAM)</a> - initial author
 * @version $Revision: 1.13 $ 
 */
class Parser {

    private static String R(String key) { return JNLPRuntime.getMessage(key); }
    private static String R(String key, Object p1) { return R(key, p1, null); }
    private static String R(String key, Object p1, Object p2) { return R(key, p1, p2, null); }
    private static String R(String key, Object p1, Object p2, Object p3) { return JNLPRuntime.getMessage(key, new Object[] { p1, p2, p3 }); }


    // defines netx.jnlp.Node class if using Tiny XML or Nano XML

    // Currently uses the Nano XML parse.  Search for "SAX" or
    // "TINY" or "NANO" and uncomment those blocks and comment the
    // active ones (if any) to switch XML parsers.  Also
    // (un)comment appropriate Node class at end of this file and
    // do a clean build.

    /**
     * Ensure consistent error handling.
     */
    /* SAX
    static ErrorHandler errorHandler = new ErrorHandler() {
        public void error(SAXParseException exception) throws SAXParseException {
            //throw exception;
        }
        public void fatalError(SAXParseException exception) throws SAXParseException {
            //throw exception;
        }
        public void warning(SAXParseException exception) {
            System.err.println("XML parse warning:");
            exception.printStackTrace();
        }
    };
    */


    /** the supported JNLP file versions */
    private static Version supportedVersions = new Version("1.0 1.5 6.0");

    // fix: some descriptors need to use the jnlp file at a later
    // date and having file ref lets us pass it to their
    // constructors
    //
    /** the file reference */
    private JNLPFile file; // do not use (uninitialized)

    /** the root node */
    private Node root;

    /** the specification version */
    private Version spec;

    /** the base URL that all hrefs are relative to */
    private URL base;

    /** the codebase URL */
    private URL codebase;

    /** the file URL */
    private URL fileLocation;

    /** whether to throw errors on non-fatal errors. */
    private boolean strict; // if strict==true parses a file with no error then strict==false should also

    /** whether to allow extensions to the JNLP specification */
    private boolean allowExtensions; // true if extensions to JNLP spec are ok


    /**
     * Create a parser for the JNLP file.  If the location
     * parameters is not null it is used as the default codebase
     * (does not override value of jnlp element's href
     * attribute).<p>
     *
     * The root node may be normalized as a side effect of this
     * constructor.
     *
     * @param file the (uninitialized) file reference
     * @param base if codebase is not specified, a default base for relative URLs 
     * @param root the root node
     * @param strict whether to enforce strict compliance with the JNLP spec
     * @param allowExtensions whether to allow extensions to the JNLP spec
     * @throws ParseException if the JNLP file is invalid
     */
    public Parser(JNLPFile file, URL base, Node root, boolean strict, boolean allowExtensions) throws ParseException {
        this.file = file;
        this.root = root;
        this.strict = strict;
        this.allowExtensions = allowExtensions;

        // ensure it's a JNLP node
        if (root == null || !root.getNodeName().equals("jnlp"))
            throw new ParseException(R("PInvalidRoot"));

        // JNLP tag information
        this.spec = getVersion(root, "spec", "1.0+");
        this.codebase = addSlash(getURL(root, "codebase", base));
        this.base = (codebase!=null) ? codebase : base; // if codebase not specified use default codebase
        fileLocation = getURL(root, "href", this.base);

        // ensure version is supported
        if (!supportedVersions.matchesAny(spec))
            throw new ParseException(R("PSpecUnsupported", supportedVersions));

        // normalize the text nodes
        root.normalize();
    }

    /**
     * Return the JNLP specification versions supported.
     */
    public static Version getSupportedVersions() {
        return supportedVersions;
    }

    /** 
     * Returns the file version.
     */
    public Version getFileVersion() {
        return getVersion(root, "version", null);
    }

    /** 
     * Returns the file location.
     */
    public URL getFileLocation() {
        return fileLocation;
    }

    /** 
     * Returns the codebase.
     */
    public URL getCodeBase() {
        return codebase;
    }

    /** 
     * Returns the specification version.
     */
    public Version getSpecVersion() {
        return spec;
    }

    // 
    // This section loads the resources elements
    // 

    /**
     * Returns all of the ResourcesDesc elements under the specified
     * node (jnlp or j2se).
     *
     * @param parent the parent node (either jnlp or j2se)
     * @param j2se true if the resources are located under a j2se or java node
     * @throws ParseException if the JNLP file is invalid
     */
    public List getResources(Node parent, boolean j2se) throws ParseException {
        List result = new ArrayList();
        Node resources[] = getChildNodes(parent, "resources");

        // ensure that there are at least one information section present
        if (resources.length == 0 && !j2se)
            throw new ParseException(R("PNoResources"));

        // create objects from the resources sections
        for (int i=0; i < resources.length; i++)
            result.add(getResourcesDesc(resources[i], j2se));

        return result;
    }

    /**
     * Returns the ResourcesDesc element at the specified node.
     *
     * @param node the resources node
     * @param j2se true if the resources are located under a j2se or java node
     * @throws ParseException if the JNLP file is invalid
     */
    public ResourcesDesc getResourcesDesc(Node node, boolean j2se) throws ParseException {
        boolean mainFlag = false; // if found a main tag

        // create resources
        ResourcesDesc resources = 
            new ResourcesDesc(file, 
                              getLocales(node), 
                              splitString(getAttribute(node, "os", null)),
                              splitString(getAttribute(node, "arch", null)));

        // step through the elements
        Node child = node.getFirstChild();
        while (child != null) {
            String name = child.getNodeName();

            // check for nativelib but no trusted environment 
            if ("nativelib".equals(name))
                if (!isTrustedEnvironment())
                    throw new ParseException(R("PUntrustedNative"));

            if ("j2se".equals(name) || "java".equals(name)) {
                if (getChildNode(root, "component-desc") != null)
                    if (strict)
                        throw new ParseException(R("PExtensionHasJ2SE"));
                if (!j2se)
                    resources.addResource( getJRE(child) );
                else
                    throw new ParseException(R("PInnerJ2SE"));
            }

            if ("jar".equals(name) || "nativelib".equals(name)) {
                JARDesc jar = getJAR(child);

                // check for duplicate main entries
                if (jar.isMain()) {
                    if (mainFlag == true)
                        if (strict)
                            throw new ParseException(R("PTwoMains"));
                    mainFlag = true;
                }

                resources.addResource(jar);
            }

            if ("extension".equals(name))
                resources.addResource( getExtension(child) );

            if ("property".equals(name)) 
                resources.addResource( getProperty(child) );

            if ("package".equals(name))
                resources.addResource( getPackage(child) );

            child = child.getNextSibling();
        }

        return resources;
    }

    /**
     * Returns the JRE element at the specified node.
     *
     * @param node the j2se/java node 
     * @throws ParseException if the JNLP file is invalid
     */
    public JREDesc getJRE(Node node) throws ParseException {
        Version version = getVersion(node, "version", null);
        URL location = getURL(node, "href", base);
        String vmArgs = getAttribute(node, "java-vm-args",null);
        try {
            checkVMArgs(vmArgs);
        } catch (IllegalArgumentException argumentException) {
            vmArgs = null;
        }
        String initialHeap = getAttribute(node, "initial-heap-size", null);
        String maxHeap = getAttribute(node, "max-heap-size", null);
        List resources = getResources(node, true);

        // require version attribute
        getRequiredAttribute(node, "version", null);

        return new JREDesc(version, location, vmArgs, initialHeap, maxHeap, resources);
    }



    /**
     * Returns the JAR element at the specified node.
     *
     * @param node the jar or nativelib node 
     * @throws ParseException if the JNLP file is invalid
     */
    public JARDesc getJAR(Node node) throws ParseException {
        boolean nativeJar = "nativelib".equals(node.getNodeName());
        URL location = getRequiredURL(node, "href", base);
        Version version = getVersion(node, "version", null);
        String part = getAttribute(node, "part", null);
        boolean main = "true".equals(getAttribute(node, "main", "false"));
        boolean lazy = "lazy".equals(getAttribute(node, "download", "eager"));
        int size = Integer.parseInt(getAttribute(node, "size", "0"));

        if (nativeJar && main)
            if (strict) 
                throw new ParseException(R("PNativeHasMain"));

        return new JARDesc(location, version, part, lazy, main, nativeJar, true);

    }

    /**
     * Returns the Extension element at the specified node.
     *
     * @param node the extension node 
     * @throws ParseException if the JNLP file is invalid
     */
    public ExtensionDesc getExtension(Node node) throws ParseException {
        String name = getAttribute(node, "name", null);
        Version version = getVersion(node, "version", null);
        URL location = getRequiredURL(node, "href", base);

        ExtensionDesc ext = new ExtensionDesc(name, version, location);

        Node dload[] = getChildNodes(node, "ext-download");
        for (int i=0; i < dload.length; i++) {
            boolean lazy = "lazy".equals(getAttribute(dload[i], "download", "eager"));

            ext.addPart(getRequiredAttribute(dload[i], "ext-part", null),
                        getAttribute(dload[i], "part", null),
                        lazy);
        }

        return ext;
    }

    /**
     * Returns the Property element at the specified node.
     *
     * @param node the property node 
     * @throws ParseException if the JNLP file is invalid
     */
    public PropertyDesc getProperty(Node node) throws ParseException {
        String name = getRequiredAttribute(node, "name", null);
        String value = getRequiredAttribute(node, "value", "");

        return new PropertyDesc(name, value);
    }

    /**
     * Returns the Package element at the specified node.
     *
     * @param node the package node 
     * @throws ParseException if the JNLP file is invalid
     */
    public PackageDesc getPackage(Node node) throws ParseException {
        String name = getRequiredAttribute(node, "name", null);
        String part = getRequiredAttribute(node, "part", "");
        boolean recursive = getAttribute(node, "recursive", "false").equals("true");

        return new PackageDesc(name, part, recursive);
    }

    // 
    // This section loads the information elements
    // 

    /**
     * Returns all of the information elements under the specified
     * node.
     *
     * @param parent the parent node (jnlp)
     * @throws ParseException if the JNLP file is invalid
     */
    public List getInfo(Node parent) throws ParseException {
        List result = new ArrayList();
        Node info[] = getChildNodes(parent, "information");

        // ensure that there are at least one information section present
        if (info.length == 0)
            throw new ParseException(R("PNoInfoElement"));

        // create objects from the info sections
        for (int i=0; i < info.length; i++)
            result.add(getInformationDesc(info[i]));

        return result;
    }

    /**
     * Returns the information element at the specified node.
     *
     * @param node the information node
     * @throws ParseException if the JNLP file is invalid
     */
    public InformationDesc getInformationDesc(Node node) throws ParseException {
        List descriptionsUsed = new ArrayList();

        // locale
        Locale locales[] = getLocales(node);

        // create information
        InformationDesc info = new InformationDesc(file, locales);

        // step through the elements
        Node child = node.getFirstChild();
        while (child != null) {
            String name = child.getNodeName();

            if ("title".equals(name))
                addInfo(info, child, null, getSpanText(child));
            if ("vendor".equals(name))
                addInfo(info, child, null, getSpanText(child));
            if ("description".equals(name)) {
                String kind = getAttribute(child, "kind", "default");
                if (descriptionsUsed.contains(kind))
                    if (strict)
                        throw new ParseException(R("PTwoDescriptions", kind));

                descriptionsUsed.add(kind);
                addInfo(info, child, kind, getSpanText(child));
            }
            if ("homepage".equals(name))
                addInfo(info, child, null, getRequiredURL(child, "href", base));
            if ("icon".equals(name))
                addInfo(info, child, getAttribute(child, "kind", "default"), getIcon(child));
            if ("offline-allowed".equals(name))
                addInfo(info, child, null, Boolean.TRUE);
            if ("sharing-allowed".equals(name)) {
                if (strict && !allowExtensions)
                    throw new ParseException(R("PSharing"));
                addInfo(info, child, null, Boolean.TRUE);
            }
            if ("association".equals(name)) {
                addInfo(info, child, null, getAssociation(child));
            }
            if ("shortcut".equals(name)) {
                addInfo(info, child, null, getShortcut(child));
            }
            if ("related-content".equals(name)) {
                addInfo(info, child, null, getRelatedContent(child));
            }

            child = child.getNextSibling();
        }

        return info;
    }

    /**
     * Adds a key,value pair to the information object.
     *
     * @param info the information object 
     * @param node node name to be used as the key
     * @param mod key name appended with "-"+mod if not null
     * @param value the info object to add (icon or string)
     */
    protected void addInfo(InformationDesc info, Node node, String mod, Object value) {
        String modStr = (mod == null) ? "" : "-"+mod;

        if (node == null)
            return;

        info.addItem(node.getNodeName()+modStr, value);
    }

    /**
     * Returns the icon element at the specified node.
     *
     * @param node the icon node
     * @throws ParseException if the JNLP file is invalid
     */
    public IconDesc getIcon(Node node) throws ParseException {
        int width = Integer.parseInt(getAttribute(node, "width", "-1"));
        int height = Integer.parseInt(getAttribute(node, "height", "-1"));
        int size = Integer.parseInt(getAttribute(node, "size", "-1"));
        int depth = Integer.parseInt(getAttribute(node, "depth", "-1"));
        URL location = getRequiredURL(node, "href", base);
        Object kind = getAttribute(node, "kind", "default"); 

        return new IconDesc(location, kind, width, height, depth, size);
    }

    // 
    // This section loads the security descriptor element
    // 

    /**
     * Returns the security descriptor element.  If no security
     * element was specified in the JNLP file then a SecurityDesc
     * with applet permissions is returned.
     *
     * @param parent the parent node
     * @throws ParseException if the JNLP file is invalid
     */
    public SecurityDesc getSecurity(Node parent) throws ParseException {
        Node nodes[] = getChildNodes(parent, "security");

        // test for too many security elements
        if (nodes.length > 1) 
            if (strict)
                throw new ParseException(R("PTwoSecurity"));

        Object type = SecurityDesc.SANDBOX_PERMISSIONS;

        if (nodes.length == 0)
            type = SecurityDesc.SANDBOX_PERMISSIONS;
        else if (null != getChildNode(nodes[0], "all-permissions"))
            type = SecurityDesc.ALL_PERMISSIONS;
        else if (null != getChildNode(nodes[0], "j2ee-application-client-permissions"))
            type = SecurityDesc.J2EE_PERMISSIONS;
        else if (strict)
            throw new ParseException(R("PEmptySecurity"));

        if (base != null)
            return new SecurityDesc(file, type, base.getHost());
        else
            return new SecurityDesc(file, type, null);
    }

    /**
     * Returns whether the JNLP file requests a trusted execution
     * environment.
     */
    protected boolean isTrustedEnvironment() {
        Node security = getChildNode(root, "security");

        if (security != null)
            if (getChildNode(security, "all-permissions") != null
                || getChildNode(security, "j2ee-application-client-permissions") != null)
                return true;

        return false;
    }

    // 
    // This section loads the launch descriptor element
    // 

    /**
     * Returns the launch descriptor element, either AppletDesc,
     * ApplicationDesc, ComponentDesc, or InstallerDesc.
     *
     * @param parent the parent node
     * @throws ParseException if the JNLP file is invalid
     */
    public Object getLauncher(Node parent) throws ParseException {
        // check for other than one application type
        if (1 != getChildNodes(parent, "applet-desc").length
            + getChildNodes(parent, "application-desc").length
            + getChildNodes(parent, "component-desc").length
            + getChildNodes(parent, "installer-desc").length)
            throw new ParseException(R("PTwoDescriptors"));

        Node child = parent.getFirstChild();
        while (child != null) {
            String name = child.getNodeName();

            if ("applet-desc".equals(name))
                return getApplet(child);
            if ("application-desc".equals(name))
                return getApplication(child);
            if ("component-desc".equals(name))
                return getComponent(child);
            if ("installer-desc".equals(name))
                return getInstaller(child);

            child = child.getNextSibling();
        }

        // not reached
        return null;
    }

    /**
     * Returns the applet descriptor.
     *
     * @throws ParseException if the JNLP file is invalid
     */
    public AppletDesc getApplet(Node node) throws ParseException {
        String name = getRequiredAttribute(node, "name", R("PUnknownApplet"));
        String main = getRequiredAttribute(node, "main-class", null);
        URL docbase = getURL(node, "documentbase", base);
        Map paramMap = new HashMap();
        int width = 0;
        int height = 0;

        try {
            width = Integer.parseInt(getRequiredAttribute(node, "width", "100"));
            height = Integer.parseInt(getRequiredAttribute(node, "height", "100"));
        }
        catch (NumberFormatException nfe) {
            if (width <= 0)
                throw new ParseException(R("PBadWidth"));
            throw new ParseException(R("PBadWidth"));
        }

        // read params
        Node params[] = getChildNodes(node, "param");
        for (int i=0; i < params.length; i++) {
            paramMap.put(getRequiredAttribute(params[i], "name", null),
                         getRequiredAttribute(params[i], "value", ""));
        }

        return new AppletDesc(name, main, docbase, width, height, paramMap);
    }

    /**
     * Returns the application descriptor.
     *
     * @throws ParseException if the JNLP file is invalid
     */
    public ApplicationDesc getApplication(Node node) throws ParseException {
        String main = getAttribute(node, "main-class", null);
        List argsList = new ArrayList();

        // if (main == null)
        //   only ok if can be found in main jar file (can't check here but make a note)

        // read parameters
        Node args[] = getChildNodes(node, "argument");
        for (int i=0; i < args.length; i++) {
            //argsList.add( args[i].getNodeValue() );
            
            //This approach was not finding the argument text
            argsList.add( getSpanText(args[i]) );
        }

        String argStrings[] = 
            (String[]) argsList.toArray( new String[argsList.size()] );

        return new ApplicationDesc(main, argStrings);
    }

    /**
     * Returns the component descriptor.
     */
    public ComponentDesc getComponent(Node node) {
        return new ComponentDesc();
    }

    /**
     * Returns the installer descriptor.
     */
    public InstallerDesc getInstaller(Node node) {
        String main = getAttribute(node, "main-class", null);

        return new InstallerDesc(main);
    }

    /**
     * Returns the association descriptor.
     */
    public AssociationDesc getAssociation(Node node) throws ParseException {
        String[] extensions = getRequiredAttribute(node, "extensions", null).split(" ");
        String mimeType = getRequiredAttribute(node, "mime-type", null);

        return new AssociationDesc(mimeType, extensions);
    }
    
    /**
     * Returns the shortcut descriptor.
     */
    public ShortcutDesc getShortcut(Node node) throws ParseException {
        
        String online = getAttribute(node, "online", "true");
        boolean shortcutIsOnline = Boolean.valueOf(online);
        
        boolean showOnDesktop = false;
        MenuDesc menu = null;
        
        // step through the elements
        Node child = node.getFirstChild();
        while (child != null) {
            String name = child.getNodeName();

            if ("desktop".equals(name)) {
                if (showOnDesktop && strict) {
                    throw new ParseException(R("PTwoDesktops"));
                }
                showOnDesktop = true;
            } else if ("menu".equals(name)){
                if (menu != null && strict) {
                    throw new ParseException(R("PTwoMenus"));
                }
                menu = getMenu(child);
            }
            
            child = child.getNextSibling();
        }
        
        ShortcutDesc shortcut = new ShortcutDesc(shortcutIsOnline, showOnDesktop);
        if (menu != null) {
            shortcut.addMenu(menu);
        }
        return shortcut;
    }
    
    /**
     * Returns the menu descriptor.
     */
    public MenuDesc getMenu(Node node) {
        String subMenu = getAttribute(node, "submenu", null);
        
        return new MenuDesc(subMenu);
    }

    
    /**
     * Returns the related-content descriptor.
     */
    public RelatedContentDesc getRelatedContent(Node node) throws ParseException {
        
        getRequiredAttribute(node, "href", null);
        URL location = getURL(node, "href", base);
        
        String title = null;
        String description = null;
        IconDesc icon = null;
        
        // step through the elements
        Node child = node.getFirstChild();
        while (child != null) {
            String name = child.getNodeName();
            
            if ("title".equals(name)) {
                if (title != null && strict) {
                    throw new ParseException(R("PTwoTitles"));
                }
                title = getSpanText(child);
            } else if ("description".equals(name)) {
                if (description != null && strict) {
                    throw new ParseException(R("PTwoDescriptions"));
                }
                description = getSpanText(child);
            } else if ("icon".equals(name)) {
                if (icon != null && strict) {
                    throw new ParseException(R("PTwoIcons"));
                }
                icon = getIcon(child);
            }
            
            child = child.getNextSibling();
        }
        
        RelatedContentDesc relatedContent = new RelatedContentDesc(location);
        relatedContent.setDescription(description);
        relatedContent.setIconDesc(icon);
        relatedContent.setTitle(title);
        
        return relatedContent;
        
    }
    
    // other methods

    /**
     * Returns an array of substrings seperated by spaces (spaces
     * escaped with backslash do not separate strings).  This method
     * splits strings as per the spec except that it does replace
     * escaped other characters with their own value.
     */
    public String[] splitString(String source) {
        if (source == null)
            return new String[0];

        List result = new ArrayList();
        StringTokenizer st = new StringTokenizer(source, " ");
        StringBuffer part = new StringBuffer();
        while (st.hasMoreTokens()) {
            part.setLength(0);

            // tack together tokens joined by backslash
            while (true) {
                part.append(st.nextToken());

                if (st.hasMoreTokens() && part.charAt(part.length()-1) == '\\')
                    part.setCharAt(part.length()-1, ' '); // join with the space
                else
                    break; // bizarre while format gets \ at end of string right (no extra space added at end)
            } 

            // delete \ quote chars
            for (int i = part.length(); i-- > 0;) // sweet syntax for reverse loop
                if (part.charAt(i) == '\\')
                    part.deleteCharAt(i--); // and skip previous char so \\ becomes \

            result.add( part.toString() );
        }

        return (String[]) result.toArray(new String[result.size()] );
    }

    /**
     * Returns the Locale object(s) from a node's locale attribute.
     *
     * @param node the node with a locale attribute
     */
    public Locale[] getLocales(Node node) {
        List locales = new ArrayList();
        String localeParts[] = 
            splitString(getAttribute(node, "locale", ""));

        for (int i=0; i < localeParts.length; i++) {
            Locale l = getLocale( localeParts[i] );
            if (l != null)
                locales.add(l);
        }

        return (Locale[]) locales.toArray(new Locale[locales.size()] );
    }

    /**
     * Returns a Locale from a single locale.
     *
     * @param locale the locale string
     */
    public Locale getLocale(String localeStr) {
        if (localeStr.length() < 2)
            return null;

        String language = localeStr.substring(0, 2);
        String country = (localeStr.length()<5) ? "" : localeStr.substring(3, 5);
        String variant = (localeStr.length()<7) ? "" : localeStr.substring(6, 8);

        // null is not allowed n locale but "" is
        return new Locale(language, country, variant);
    }



    // XML junk

    /**
     * Returns the implied text under a node, for example "text" in
     * "<description>text</description>".
     *
     * @param node the node with text under it
     * @throws ParseException if the JNLP file is invalid
     */
    public String getSpanText(Node node) throws ParseException {
        if (node == null)
            return null;

        // NANO
        return node.getNodeValue();

        /* TINY
        Node child = node.getFirstChild();

        if (child == null) {
            if (strict)
                // not sure if this is an error or whether "" is proper
                throw new ParseException("No text specified (node="+node.getNodeName()+")");
            else
                return "";
        }

        return child.getNodeValue();
        */
    }

    /**
     * Returns the first child node with the specified name.
     */
    public static Node getChildNode(Node node, String name) {
        Node[] result = getChildNodes(node, name);
        if (result.length == 0)
            return null;
        else
            return result[0];
    }

    /**
     * Returns all child nodes with the specified name.
     */
    public static Node[] getChildNodes(Node node, String name) {
        List result = new ArrayList();

        Node child = node.getFirstChild();
        while (child != null) {
            if (child.getNodeName().equals(name))
                result.add(child);
            child = child.getNextSibling();
        }

        return (Node[]) result.toArray( new Node[result.size()] );
    }


    /**
     * Returns a URL with a trailing / appended to it if there is no
     * trailing slash on the specifed URL.
     */
    private URL addSlash(URL source) {
        if (source == null)
            return null;

        if (!source.toString().endsWith("/")) {
            try { 
                source = new URL(source.toString()+"/"); 
            }
            catch (MalformedURLException ex) {
            }
        }

        return source;
    }


    /**
     * Returns the same result as getURL except that a
     * ParseException is thrown if the attribute is null or empty.
     *
     * @param node the node
     * @param name the attribute containing an href
     * @param base the base URL
     * @throws ParseException if the JNLP file is invalid
     */
    public URL getRequiredURL(Node node, String name, URL base) throws ParseException {
        // probably should change "" to null so that url is always
        // required even if !strict
        getRequiredAttribute(node, name, "");

        return getURL(node, name, base);
    }


    /**
     * Returns a URL object from a href string relative to the
     * code base.  If the href denotes a relative URL, it must
     * reference a location that is a subdirectory of the
     * codebase.<p>
     *
     * @param node the node
     * @param name the attribute containing an href
     * @param base the base URL
     * @throws ParseException if the JNLP file is invalid
     */
    public URL getURL(Node node, String name, URL base) throws ParseException {
        String href = getAttribute(node, name, null);
        if (href == null)
            return null; // so that code can throw an exception if attribute was required

        try {
            if (base == null)
                return new URL(href);
            else {
                try {
                    return new URL(href);
                }
                catch (MalformedURLException ex) {
                    // is relative
                }

                URL result = new URL(base, href);

                // check for going above the codebase
                if (! result.toString().startsWith( base.toString()) )
                    if (strict)
                        throw new ParseException(R("PUrlNotInCodebase", node.getNodeName(), href, base));

                return result;
            }

        }
        catch (MalformedURLException ex) {
            if (base == null)
                throw new ParseException(R("PBadNonrelativeUrl", node.getNodeName(), href));
            else
                throw new ParseException(R("PBadRelativeUrl", node.getNodeName(), href, base));
        }
    }

    /**
     * Returns a Version from the specified attribute and default
     * value.
     *
     * @param node the node
     * @param name the attribute
     * @param defaultValue default if no such attribute
     * @return a Version, or null if no such attribute and default is null
     */
    public Version getVersion(Node node, String name, String defaultValue) {
        String version = getAttribute(node, name, defaultValue);
        if (version == null)
            return null;
        else
            return new Version(version);
    }
    
    /**
     * Check that the VM args are valid and safe
     * @param vmArgs a string containing the args
     * @throws ParseException if the VM arguments are invalid or dangerous
     */
    private void checkVMArgs(String vmArgs) throws IllegalArgumentException {
        if (vmArgs == null) {
            return;
        }
        
        List<String> validArguments = Arrays.asList(getValidVMArguments());
        List<String> validStartingArguments = Arrays.asList(getValidStartingVMArguments());
        
        String[] arguments = vmArgs.split(" ");
        boolean argumentIsValid = false;
        for (String argument: arguments) {
            argumentIsValid = false;
            
            if (validArguments.contains(argument)) {
                argumentIsValid = true;
            } else {
                for (String validStartingArgument: validStartingArguments) {
                    if (argument.startsWith(validStartingArgument)) {
                        argumentIsValid = true;
                        break;
                    }
                }
            }
            
            if (!argumentIsValid) {
                throw new IllegalArgumentException(argument);
            }
        }
        
    }
    
    /**
     * Returns an array of valid (ie safe and supported) arguments for the JVM
     * 
     * Based on http://java.sun.com/javase/6/docs/technotes/guides/javaws/developersguide/syntax.html
     */
    private String[] getValidVMArguments() {
        return new String[] {
        "-d32",                             /* use a 32-bit data model if available */
        "-client",                          /* to select the client VM */
        "-server",                          /* to select the server VM */
        "-verbose",                         /* enable verbose output */
        "-version",                         /* print product version and exit */
        "-showversion",                     /* print product version and continue */                              
        "-help",                            /* print this help message */
        "-X",                               /* print help on non-standard options */
        "-ea",                              /* enable assertions */
        "-enableassertions",                /* enable assertions */
        "-da",                              /* disable assertions */
        "-disableassertions",               /* disable assertions */
        "-esa",                             /* enable system assertions */
        "-enablesystemassertions",          /* enable system assertions */
        "-dsa",                             /* disable system assertione */
        "-disablesystemassertions",         /* disable system assertione */
        "-Xmixed",                          /* mixed mode execution (default) */
        "-Xint",                            /* interpreted mode execution only */
        "-Xnoclassgc",                      /* disable class garbage collection */
        "-Xincgc",                          /* enable incremental garbage collection */
        "-Xbatch",                          /* disable background compilation */
        "-Xprof",                           /* output cpu profiling data */
        "-Xdebug",                          /* enable remote debugging */
        "-Xfuture",                         /* enable strictest checks, anticipating future default */
        "-Xrs",                             /* reduce use of OS signals by Java/VM (see documentation) */
        "-XX:+ForceTimeHighResolution",     /* use high resolution timer */
        "-XX:-ForceTimeHighResolution",     /* use low resolution (default) */
        };
    }

    /**
     * Returns an array containing the starts of valid (ie safe and supported)
     * arguments for the JVM
     * 
     * Based on http://java.sun.com/javase/6/docs/technotes/guides/javaws/developersguide/syntax.html
     */
    private String[] getValidStartingVMArguments() {
        return new String[] {
        "-ea",                          /* enable assertions for classes */
        "-enableassertions",            /* enable assertions for classes */
        "-da",                          /* disable assertions for classes */
        "-disableassertions",           /* disable assertions for classes */
        "-verbose",                     /* enable verbose output */
        "-Xms",                         /* set initial Java heap size */
        "-Xmx",                         /* set maximum Java heap size */
        "-Xss",                         /* set java thread stack size */
        "-XX:NewRatio",                 /* set Ratio of new/old gen sizes */
        "-XX:NewSize",                  /* set initial size of new generation */
        "-XX:MaxNewSize",               /* set max size of new generation */
        "-XX:PermSize",                 /* set initial size of permanent gen */
        "-XX:MaxPermSize",              /* set max size of permanent gen */
        "-XX:MaxHeapFreeRatio",         /* heap free percentage (default 70) */
        "-XX:MinHeapFreeRatio",         /* heap free percentage (default 40) */
        "-XX:UseSerialGC",              /* use serial garbage collection */
        "-XX:ThreadStackSize",          /* thread stack size (in KB) */
        "-XX:MaxInlineSize",            /* set max num of bytecodes to inline */
        "-XX:ReservedCodeCacheSize",    /* Reserved code cache size (bytes) */
        "-XX:MaxDirectMemorySize",

        };
    }

    /**
     * Returns the same result as getAttribute except that if strict
     * mode is enabled or the default value is null a parse
     * exception is thrown instead of returning the default value.
     *
     * @param node the node
     * @param name the attribute
     * @param defaultValue default value
     * @throws ParseException if the attribute does not exist or is empty
     */
    public String getRequiredAttribute(Node node, String name, String defaultValue) throws ParseException {
        String result = getAttribute(node, name, null);

        if (result == null || result.length() == 0)
            if (strict || defaultValue == null)
                throw new ParseException(R("PNeedsAttribute", node.getNodeName(), name));

        if (result == null)
            return defaultValue;
        else
            return result;
    }

    /**
     * Retuns an attribute or the specified defaultValue if there is
     * no such attribute.
     *
     * @param node the node
     * @param name the attribute
     * @param defaultValue default if no such attribute
     */
    public String getAttribute(Node node, String name, String defaultValue) {
        // SAX
        // String result = ((Element) node).getAttribute(name);
        String result = node.getAttribute(name);

        if (result == null || result.length()==0) 
            return defaultValue;

        return result;
    }

    /**
     * Return the root node from the XML document in the specified
     * input stream.
     *
     * @throws ParseException if the JNLP file is invalid
     */
    public static Node getRootNode(InputStream input) throws ParseException {
        try {
            /* SAX
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(errorHandler);

            Document doc = builder.parse(input);
            return doc.getDocumentElement();
            */

            /* TINY
            Node document = new Node(TinyParser.parseXML(input));
            Node jnlpNode = getChildNode(document, "jnlp"); // skip comments
            */
            
            //A BufferedInputStream is used to allow marking and reseting 
            //of a stream.    
            BufferedInputStream bs = new BufferedInputStream(input);

            /* NANO */
            final XMLElement xml = new XMLElement();
            final PipedInputStream pin = new PipedInputStream();
            final PipedOutputStream pout = new PipedOutputStream(pin);   
            final InputStreamReader isr = new InputStreamReader(bs, getEncoding(bs));    
            // Clean the jnlp xml file of all comments before passing
            // it to the parser.
            new Thread(
                new Runnable(){
                    public void run(){
                        (new XMLElement()).sanitizeInput(isr, pout);
                        try {
                            pout.close();
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                        }
                    }
                }
            ).start();
            xml.parseFromReader(new InputStreamReader(pin));
            Node jnlpNode = new Node(xml);
            return jnlpNode;
        }
        catch(Exception ex) {
            throw new ParseException(R("PBadXML"), ex);
        }
    }
    
    /**
     * Returns the name of the encoding used in this InputStream.
     *
     * @param input the InputStream
     * @return a String representation of encoding
     */
    private static String getEncoding(InputStream input) throws IOException{
        //Fixme: This only recognizes UTF-8, UTF-16, and 
        //UTF-32, which is enough to parse the prolog portion of xml to
        //find out the exact encoding (if it exists). The reason being
        //there could be other encodings, such as ISO 8859 which is 8-bits
        //but it supports latin characters.  
        //So what needs to be done is to parse the prolog and retrieve
        //the exact encoding from it.

        int[] s = new int[4];
        String encoding = "UTF-8";

        //Determine what the first four bytes are and store 
        //them into an int array.
        input.mark(4);
        for (int i = 0; i < 4; i++) {
            s[i] = input.read(); 
        }
        input.reset();

        //Set the encoding base on what the first four bytes of the
        //inputstream turn out to be (following the information from
        //www.w3.org/TR/REC-xml/#sec-guessing).
        if (s[0] == 255) {
            if (s[1] == 254) {
                if (s[2] != 0 || s[3] != 0) {
                    encoding = "UnicodeLittle";
                } else {
                    encoding = "X-UTF-32LE-BOM";
                }
            }
        } else if (s[0] == 254 && s[1] == 255 && (s[2] != 0 || 
          s[3] != 0)) {
            encoding = "UTF-16";

        } else if (s[0] == 0 && s[1] == 0 && s[2] == 254 && 
          s[3] == 255) {
            encoding = "X-UTF-32BE-BOM";

        } else if (s[0] == 0 && s[1] == 0 && s[2] == 0 && 
          s[3] == 60) {
            encoding = "UTF-32BE";
 
        } else if (s[0] == 60 && s[1] == 0 && s[2] == 0 && 
          s[3] == 0) {
            encoding = "UTF-32LE";

        } else if (s[0] == 0 && s[1] == 60 && s[2] == 0 && 
          s[3] == 63) { 
            encoding = "UTF-16BE"; 
        } else if (s[0] == 60 && s[1] == 0 && s[2] == 63 &&
          s[3] == 0) { 
            encoding = "UTF-16LE";
        }

        return encoding;
    }
}

