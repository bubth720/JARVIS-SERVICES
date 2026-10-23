// Standalone JVM reference codec. Not installed into any Android application.
package org.jarvis.contracts.v1;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.nio.*;
import java.nio.charset.*;
import java.time.OffsetDateTime;

public final class ContractSupport {
    public static final int MAX_BYTES = 65536;
    private ContractSupport() {}
    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> object(Object value) {
        require(value instanceof Map, "object required");
        return (Map<String,Object>) value;
    }
    @SuppressWarnings("unchecked")
    private static List<Object> array(Object value) {
        require(value instanceof List, "array required");
        return (List<Object>) value;
    }
    private static Object freeze(Object value) {
        if(value instanceof Map) return freezeObject(object(value));
        if(value instanceof List) {
            List<Object> out=new ArrayList<>();
            for(Object item:array(value))out.add(freeze(item));
            return Collections.unmodifiableList(out);
        }
        return value;
    }
    public static Map<String,Object> freezeObject(Map<String,Object> value) {
        Map<String,Object> out=new LinkedHashMap<>();
        for(Map.Entry<String,Object> item:value.entrySet())out.put(item.getKey(),freeze(item.getValue()));
        return Collections.unmodifiableMap(out);
    }
    public static <T> List<T> mapList(Object value, Function<Object,T> mapper) {
        if(value==null)return null;
        List<T> result=new ArrayList<>();
        for(Object item:array(value))result.add(mapper.apply(item));
        return Collections.unmodifiableList(result);
    }
    public static Object parseUtf8(byte[] bytes) {
        require(bytes.length<=MAX_BYTES,"JSON size limit");
        try {
            return parse(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString());
        } catch(CharacterCodingException e) { throw new IllegalArgumentException("invalid UTF-8",e); }
    }
    public static Object parse(String text) {
        validUnicode(text);
        require(text.getBytes(StandardCharsets.UTF_8).length<=MAX_BYTES,"JSON size limit");
        Reader reader=new Reader(text);
        Object result=reader.value(0);reader.space();require(reader.i==text.length(),"trailing JSON");
        return result;
    }
    private static void validUnicode(String text) {
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(Character.isHighSurrogate(c))require(++i<text.length()&&Character.isLowSurrogate(text.charAt(i)),"unpaired surrogate");
            else require(!Character.isLowSurrogate(c),"unpaired surrogate");
        }
    }
    private static final class Reader {
        final String s;int i;
        Reader(String s){this.s=s;}
        void space(){while(i<s.length()&&" \t\r\n".indexOf(s.charAt(i))>=0)i++;}
        char take(){require(i<s.length(),"incomplete JSON");return s.charAt(i++);}
        Object value(int depth) {
            require(depth<=64,"JSON depth limit");space();require(i<s.length(),"missing JSON");char c=s.charAt(i);
            if(c=='"')return string();
            if(c=='{') {
                i++;space();Map<String,Object> out=new LinkedHashMap<>();
                if(i<s.length()&&s.charAt(i)=='}'){i++;return out;}
                while(true) {
                    space();require(i<s.length()&&s.charAt(i)=='"',"object key required");String key=string();
                    require(!out.containsKey(key),"duplicate field");space();require(take()==':',"colon required");
                    out.put(key,value(depth+1));space();char end=take();if(end=='}')return out;require(end==',',"comma required");
                }
            }
            if(c=='[') {
                i++;space();List<Object> out=new ArrayList<>();
                if(i<s.length()&&s.charAt(i)==']'){i++;return out;}
                while(true){out.add(value(depth+1));space();char end=take();if(end==']')return out;require(end==',',"comma required");}
            }
            for(String word:new String[]{"true","false","null"})if(s.startsWith(word,i)) {
                i+=word.length();return word.equals("null")?null:Boolean.valueOf(word);
            }
            int start=i;if(c=='-')i++;
            require(i<s.length()&&s.charAt(i)>='0'&&s.charAt(i)<='9',"integer required");
            if(s.charAt(i)=='0')i++;else while(i<s.length()&&s.charAt(i)>='0'&&s.charAt(i)<='9')i++;
            try{return Long.valueOf(s.substring(start,i));}catch(NumberFormatException e){throw new IllegalArgumentException("integer overflow",e);}
        }
        String string() {
            require(take()=='"',"string required");StringBuilder out=new StringBuilder();
            while(true) {
                char c=take();if(c=='"'){String value=out.toString();validUnicode(value);return value;}
                require(c>=32,"control character");
                if(c!='\\'){out.append(c);continue;}
                c=take();
                switch(c) {
                    case '"':case '\\':case '/':out.append(c);break;
                    case 'b':out.append('\b');break;case 'f':out.append('\f');break;
                    case 'n':out.append('\n');break;case 'r':out.append('\r');break;case 't':out.append('\t');break;
                    case 'u':
                        require(i+4<=s.length(),"incomplete escape");
                        String hex=s.substring(i,i+4);require(hex.matches("[0-9a-fA-F]{4}"),"invalid escape");
                        out.append((char)Integer.parseInt(hex,16));i+=4;break;
                    default:throw new IllegalArgumentException("invalid escape");
                }
            }
        }
    }
    public static String json(Object value) {
        StringBuilder out=new StringBuilder();write(value,out,0);return out.toString();
    }
    private static int compareUnicode(String a,String b) {
        PrimitiveIterator.OfInt x=a.codePoints().iterator(),y=b.codePoints().iterator();
        while(x.hasNext()&&y.hasNext()){int c=Integer.compare(x.nextInt(),y.nextInt());if(c!=0)return c;}
        return Boolean.compare(x.hasNext(),y.hasNext());
    }
    private static void write(Object value,StringBuilder out,int depth) {
        require(depth<=64,"JSON depth limit");
        if(value==null){out.append("null");return;}
        if(value instanceof String) {
            String s=(String)value;validUnicode(s);out.append('"');
            for(int i=0;i<s.length();i++) {
                char c=s.charAt(i);switch(c) {
                    case '"':out.append("\\\"");break;case '\\':out.append("\\\\");break;
                    case '\b':out.append("\\b");break;case '\f':out.append("\\f");break;
                    case '\n':out.append("\\n");break;case '\r':out.append("\\r");break;case '\t':out.append("\\t");break;
                    default:if(c<32)out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else out.append(c);
                }
            }
            out.append('"');return;
        }
        if(value instanceof Boolean||value instanceof Long){out.append(value);return;}
        if(value instanceof Map) {
            Map<String,Object> m=object(value);List<String> keys=new ArrayList<>(m.keySet());keys.sort(ContractSupport::compareUnicode);
            out.append('{');boolean first=true;for(String key:keys){if(!first)out.append(',');first=false;write(key,out,depth+1);out.append(':');write(m.get(key),out,depth+1);}out.append('}');return;
        }
        if(value instanceof List) {
            out.append('[');boolean first=true;for(Object item:array(value)){if(!first)out.append(',');first=false;write(item,out,depth+1);}out.append(']');return;
        }
        throw new IllegalArgumentException("unsupported JSON value");
    }
    private static long n(Map<String,Object> m,String k){return (Long)m.get(k);}
    private static boolean b(Map<String,Object> m,String k){return Boolean.TRUE.equals(m.get(k));}
    public static void validate(String name,Map<String,Object> value,Map<String,Object> schema) {
        // Copy first: callers cannot mutate a map while validation/serialization observes it.
        require(json(value).getBytes(StandardCharsets.UTF_8).length<=MAX_BYTES,"JSON size limit");
        check(value,schema);
        if(name.equals("request-envelope")) {
            Map<String,Object> d=object(value.get("deadline")),l=object(value.get("limits")),p=object(value.get("payload"));
            long duration=n(d,"expires_at_ms")-n(d,"sent_at_ms");require(duration>0&&duration<=n(l,"timeout_ms"),"invalid deadline");
            if(value.get("capability").equals("text.generate"))require(p.containsKey("model_sha256"),"model digest required");
            if(value.get("capability").equals("speech.synthesize"))require(p.containsKey("voice_profile_ref")&&p.containsKey("pack_sha256"),"voice binding required");
        }
        if(name.equals("resource-lease")) {
            String state=(String)value.get("state");
            if(state.equals("released"))require(b(value,"effective_end")&&b(value,"release_confirmed")&&!b(value,"model_resident"),"unconfirmed release");
            else require(!b(value,"release_confirmed"),"inconsistent release");
            if(state.equals("stop_requested"))require(b(value,"cancellation_requested"),"inconsistent cancellation");
            if(value.get("decision").equals("refused"))require(!b(value,"model_resident")&&state.equals("idle"),"resident refusal");
        }
        if(name.equals("capability-manifest")) {
            Set<Object> ids=new HashSet<>();for(Object item:array(value.get("capabilities"))) {
                Map<String,Object> cap=object(item);require(ids.add(cap.get("id")),"duplicate capability");
                require(value.get("state").equals("available")||!b(cap,"available"),"unavailable capability");
            }
        }
        if(name.equals("health-status"))require(!value.get("state").equals("unknown")||!value.get("reason_code").equals("OK"),"unknown health");
    }
    public static void validateExchange(Map<String,Object> request,Map<String,Object> response) {
        require(Objects.equals(request.get("correlation_id"),response.get("correlation_id")),"unmatched response");
        require(Objects.equals(request.get("destination"),object(response.get("provenance")).get("module_id")),"wrong responder");
    }
    private static void check(Object value,Map<String,Object> schema) {
        if(schema.containsKey("type")) {
            String type=(String)schema.get("type");
            boolean ok=type.equals("object")&&value instanceof Map||type.equals("array")&&value instanceof List||
                type.equals("string")&&value instanceof String||type.equals("integer")&&value instanceof Long||
                type.equals("boolean")&&value instanceof Boolean||type.equals("null")&&value==null;
            require(ok,"type mismatch");
        }
        if(schema.containsKey("const"))require(Objects.equals(value,schema.get("const")),"constant mismatch");
        if(schema.containsKey("enum"))require(array(schema.get("enum")).contains(value),"enum mismatch");
        if(value instanceof Map) {
            Map<String,Object> v=object(value),properties=schema.containsKey("properties")?object(schema.get("properties")):Collections.emptyMap();
            if(schema.containsKey("required"))for(Object key:array(schema.get("required")))require(v.containsKey(key),"missing field");
            if(Boolean.FALSE.equals(schema.get("additionalProperties")))require(properties.keySet().containsAll(v.keySet()),"unknown field");
            for(String key:properties.keySet())if(v.containsKey(key))check(v.get(key),object(properties.get(key)));
        }
        if(value instanceof List) {
            List<Object> values=array(value);bounds(values.size(),schema,"minItems","maxItems");
            if(Boolean.TRUE.equals(schema.get("uniqueItems")))require(new HashSet<>(values).size()==values.size(),"duplicate item");
            if(schema.containsKey("items"))for(Object item:values)check(item,object(schema.get("items")));
        }
        if(value instanceof String) {
            String s=(String)value;bounds(s.codePointCount(0,s.length()),schema,"minLength","maxLength");
            if(schema.containsKey("pattern"))require(Pattern.compile((String)schema.get("pattern")).matcher(s).find(),"pattern mismatch");
            if("uuid".equals(schema.get("format")))require(UUID.fromString(s).toString().equals(s),"noncanonical UUID");
            if("date-time".equals(schema.get("format")))require(OffsetDateTime.parse(s).getOffset().getTotalSeconds()==0,"UTC required");
        }
        if(value instanceof Long)bounds((Long)value,schema,"minimum","maximum");
        if(schema.containsKey("allOf"))for(Object item:array(schema.get("allOf")))check(value,object(item));
        if(schema.containsKey("oneOf")) {
            int count=0;for(Object item:array(schema.get("oneOf")))try{check(value,object(item));count++;}catch(IllegalArgumentException ignored){}
            require(count==1,"exactly one alternative required");
        }
    }
    private static void bounds(long value,Map<String,Object> schema,String min,String max) {
        require(!schema.containsKey(min)||value>=n(schema,min),"minimum limit");
        require(!schema.containsKey(max)||value<=n(schema,max),"maximum limit");
    }
}
