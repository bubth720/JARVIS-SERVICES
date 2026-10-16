package fr.jarvis.services.modules.documents;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import com.google.gson.*;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.shared.storage.Store;
import fr.jarvis.services.shared.tasks.Task;
import fr.jarvis.services.shared.api.Contracts.Failure;

public final class Documents {
    public interface PdfText { String extract(byte[] bytes) throws Exception; }
    public static final int MAX_BYTES=2*1024*1024, MAX_TEXT=250_000;
    private final Guard guard;private final Store store;private final PdfText pdf;
    public Documents(Guard guard,Store store,PdfText pdf){this.guard=guard;this.store=store;this.pdf=pdf;}
    public Map<String,Object> importBytes(Context ctx,String name,byte[] bytes,Task task) {
        guard.require(ctx,"documents.import");
        if(name==null || name.length()>200 || bytes.length>MAX_BYTES)throw new Failure("INPUT_TOO_LARGE");
        return task.heavy(() -> {
            task.progress("extract",10);String ext=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
            String text;
            switch(ext){
                case "pdf": if(pdf==null)throw new Failure("PDF_UNAVAILABLE");text=pdf.extract(bytes);if(text.trim().isEmpty())throw new Failure("OCR_REQUIRED");break;
                case "docx":text=readDocx(bytes);break;
                case "txt":case "md":case "csv":text=new String(bytes,StandardCharsets.UTF_8);break;
                case "json":text=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).toString();break;
                default:throw new Failure("UNSUPPORTED_FORMAT");
            }
            if(text.length()>MAX_TEXT)throw new Failure("TEXT_TOO_LARGE");task.check();
            String id=UUID.randomUUID().toString();Map<String,Object> doc=new LinkedHashMap<>();
            doc.put("id",id);doc.put("name",name);doc.put("format",ext);doc.put("sha256",Guard.digest(bytes));doc.put("text_sha256",Guard.digest(text));doc.put("text",text);doc.put("created_at",System.currentTimeMillis());doc.put("sections",sections(text));
            Path original=guard.path(ctx,"documents.import","original-"+id+"."+ext);
            Files.write(original,bytes,StandardOpenOption.CREATE_NEW);doc.put("original",original.getFileName().toString());
            try {task.check();store.put("documents",id,doc);}catch(RuntimeException e){Files.deleteIfExists(original);throw e;}
            return doc;
        });
    }
    private static List<Map<String,Object>> sections(String text) {
        List<Map<String,Object>> out=new ArrayList<>();int offset=0;
        for(String line:text.split("\n",-1)){if(!line.trim().isEmpty()){Map<String,Object> s=new LinkedHashMap<>();s.put("offset",offset);s.put("quote",line);out.add(s);}offset+=line.length()+1;}
        return out;
    }
    public Map<String,Object> get(Context ctx,String id){guard.require(ctx,"documents.read");Map<String,Object> d=store.get("documents",id);if(d==null)throw new Failure("NOT_FOUND");return d;}
    public List<Map<String,Object>> search(Context ctx,String query){guard.require(ctx,"documents.read");if(query==null||query.isEmpty())throw new Failure("INVALID_INPUT");List<Map<String,Object>> out=new ArrayList<>();for(Map<String,Object> d:store.all("documents"))if(((String)d.get("text")).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))out.add(d);return out;}
    public Map<String,Object> summarize(Context ctx,String id){Map<String,Object>d=get(ctx,id);String text=(String)d.get("text");Map<String,Object> s=new LinkedHashMap<>();s.put("kind","extractive_fact");s.put("source_id",id);s.put("sha256",d.get("text_sha256"));s.put("quote",text.substring(0,Math.min(600,text.length())));return s;}
    public Map<String,Object> compare(Context ctx,String a,String b){String x=(String)get(ctx,a).get("text"),y=(String)get(ctx,b).get("text");Set<String> left=new LinkedHashSet<>(Arrays.asList(x.split("\n"))),right=new LinkedHashSet<>(Arrays.asList(y.split("\n")));Set<String> common=new LinkedHashSet<>(left);common.retainAll(right);left.removeAll(common);right.removeAll(common);Map<String,Object> out=new LinkedHashMap<>();out.put("sources",Arrays.asList(a,b));out.put("common",common);out.put("only_left",left);out.put("only_right",right);return out;}
    public Map<String,Object> export(Context ctx,String relative,String format,String text,Task task) {
        guard.require(ctx,"documents.export");task.check();if(text==null||text.length()>MAX_TEXT)throw new Failure("INPUT_TOO_LARGE");
        byte[] bytes;
        if(format.equals("docx"))bytes=docx(text);
        else if(Arrays.asList("txt","md","csv","json").contains(format)){if(format.equals("json"))JsonParser.parseString(text);bytes=text.getBytes(StandardCharsets.UTF_8);}
        else throw new Failure("UNSUPPORTED_FORMAT");
        Path path=guard.path(ctx,"documents.export",relative);
        try {Files.write(path,bytes,StandardOpenOption.CREATE_NEW);byte[] check=Files.readAllBytes(path);if(!Guard.digest(bytes).equals(Guard.digest(check)))throw new Failure("INTEGRITY_FAILED");Map<String,Object> out=new LinkedHashMap<>();out.put("path",relative);out.put("sha256",Guard.digest(check));out.put("bytes",check.length);return out;}catch(FileAlreadyExistsException e){throw new Failure("WOULD_OVERWRITE");}catch(IOException e){throw new Failure("STORAGE_ERROR");}
    }
    public static String readDocx(byte[] bytes)throws Exception {
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){if(!entry.getName().equals("word/document.xml"))continue;ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=zip.read(buffer))!=-1){if(out.size()+n>MAX_BYTES)throw new Failure("INPUT_TOO_LARGE");out.write(buffer,0,n);}
            DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);f.setXIncludeAware(false);f.setExpandEntityReferences(false);
            Document d=f.newDocumentBuilder().parse(new ByteArrayInputStream(out.toByteArray()));NodeList paragraphs=d.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","p");StringBuilder text=new StringBuilder();for(int i=0;i<paragraphs.getLength();i++)text.append(paragraphs.item(i).getTextContent()).append('\n');return text.toString();
        }}throw new Failure("INVALID_DOCX");
    }
    private static byte[] docx(String text) {
        try {ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)){
            zip(zip,"[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
            zip(zip,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
            String escaped=text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");zip(zip,"word/document.xml","<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t xml:space=\"preserve\">"+escaped+"</w:t></w:r></w:p></w:body></w:document>");
        }return out.toByteArray();}catch(IOException e){throw new Failure("EXPORT_FAILED");}
    }
    private static void zip(ZipOutputStream zip,String name,String text)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(text.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
}
