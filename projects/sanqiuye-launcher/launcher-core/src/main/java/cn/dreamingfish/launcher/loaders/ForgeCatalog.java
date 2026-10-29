package cn.dreamingfish.launcher.loaders;
import cn.dreamingfish.launcher.GameInstance.LoaderKind;
import cn.dreamingfish.launcher.downloads.*;
import java.io.*;
import java.net.URI;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.XMLConstants;

public final class ForgeCatalog {
 private final RemoteClient remote;
 public ForgeCatalog(RemoteClient remote){this.remote=remote;}
 public List<LoaderVersions.Version> versions(LoaderKind kind,String game,CancellationToken token)throws IOException{var request=new RemoteRequest(service(kind),URI.create(root(kind)+"maven-metadata.xml"),Map.of());return parse(remote.bytes(request,2*1024*1024,token),kind,game).stream().map(v->new LoaderVersions.Version(v,!v.contains("-"))).toList();}
 static RemoteService service(LoaderKind kind){return switch(kind){case FORGE->RemoteService.FORGE_MAVEN;case NEOFORGE->RemoteService.NEOFORGE_MAVEN;default->throw new IllegalArgumentException("非Forge核心");};}
 static String root(LoaderKind kind){return switch(kind){case FORGE->"https://maven.minecraftforge.net/net/minecraftforge/forge/";case NEOFORGE->"https://maven.neoforged.net/releases/net/neoforged/neoforge/";default->throw new IllegalArgumentException("非Forge核心");};}
 public static List<String> parse(byte[] bytes,LoaderKind kind,String game)throws IOException{
  LoaderIdentity.validateVersion(game);if(bytes.length>2*1024*1024)throw new IOException("核心版本目录过大");try{
   var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
   var builder=factory.newDocumentBuilder();builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler(){@Override public void fatalError(org.xml.sax.SAXParseException e)throws org.xml.sax.SAXException{throw e;}});var doc=builder.parse(new ByteArrayInputStream(bytes));if(!doc.getDocumentElement().getTagName().equals("metadata"))throw new IOException("核心版本目录根节点无效");
   String prefix=kind==LoaderKind.FORGE?game+"-":game.matches("1\\.[0-9]+(?:\\.[0-9]+)?")?game.substring(2)+(game.substring(2).contains(".")?".":".0."):"unsupported/";var nodes=doc.getElementsByTagName("version");if(nodes.getLength()>30000)throw new IOException("核心版本目录数量无效");var values=new HashSet<String>();for(int i=0;i<nodes.getLength();i++){String v=nodes.item(i).getTextContent();if(v.startsWith(prefix)){String value=kind==LoaderKind.FORGE?v.substring(prefix.length()):v;LoaderIdentity.validateVersion(value);values.add(value);}}
   return values.stream().sorted(LoaderVersions::newestFirst).toList();
  }catch(IOException failure){throw failure;}catch(Exception invalid){throw new IOException("核心官方版本XML无效",invalid);}
 }
}
