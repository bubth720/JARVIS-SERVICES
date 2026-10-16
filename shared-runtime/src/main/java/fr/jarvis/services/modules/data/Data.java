package fr.jarvis.services.modules.data;

import java.util.*;
import java.math.*;
import com.google.gson.*;
import fr.jarvis.services.shared.api.Contracts.Failure;

/** Deterministic, bounded, exact decimal transformations. No model dependency. */
public final class Data {
    public static final int MAX_ROWS=5000;
    public static final class Table {
        public final List<String> columns;
        public final List<List<String>> rows;
        public final List<String> history;
        public Table(List<String> columns,List<List<String>> rows,List<String> history){
            if(columns.isEmpty()||columns.size()>100||new HashSet<>(columns).size()!=columns.size()||columns.contains("")||rows.size()>MAX_ROWS)throw new Failure("INVALID_TABLE");
            for(List<String> row:rows)if(row.size()!=columns.size()||row.contains(null))throw new Failure("INVALID_TABLE");
            this.columns=Collections.unmodifiableList(new ArrayList<>(columns));List<List<String>> copy=new ArrayList<>();for(List<String> r:rows)copy.add(Collections.unmodifiableList(new ArrayList<>(r)));this.rows=Collections.unmodifiableList(copy);this.history=Collections.unmodifiableList(new ArrayList<>(history));
        }
        private Table next(List<List<String>> rows,String op){List<String> h=new ArrayList<>(history);h.add(op);return new Table(columns,rows,h);}
        private int col(String name){int i=columns.indexOf(name);if(i<0)throw new Failure("UNKNOWN_COLUMN");return i;}
        public Table deduplicate(){return next(new ArrayList<>(new LinkedHashSet<>(rows)),"deduplicate_exact");}
        public Table filter(String col,String value){int c=col(col);List<List<String>> out=new ArrayList<>();for(List<String> r:rows)if(r.get(c).equals(value))out.add(r);return next(out,"filter:"+col);}
        public Table sort(String column,boolean numeric){int c=col(column);List<List<String>> out=new ArrayList<>(rows);out.sort((a,b)->numeric?decimal(a.get(c)).compareTo(decimal(b.get(c))):a.get(c).compareTo(b.get(c)));return next(out,"sort:"+column);}
        public Table normalize(){List<List<String>> out=new ArrayList<>();for(List<String> r:rows){List<String> n=new ArrayList<>();for(String v:r)n.add(java.text.Normalizer.normalize(v.trim(),java.text.Normalizer.Form.NFC));out.add(n);}return next(out,"normalize_nfc_trim");}
        public Map<String,String> stats(String column){int c=col(column);if(rows.isEmpty())throw new Failure("EMPTY_DATA");BigDecimal sum=BigDecimal.ZERO,min=null,max=null;for(List<String> r:rows){BigDecimal v=decimal(r.get(c));sum=sum.add(v);min=min==null?v:min.min(v);max=max==null?v:max.max(v);}Map<String,String> out=new LinkedHashMap<>();out.put("count",Integer.toString(rows.size()));out.put("sum",sum.toPlainString());out.put("min",min.toPlainString());out.put("max",max.toPlainString());out.put("mean",sum.divide(BigDecimal.valueOf(rows.size()),MathContext.DECIMAL128).toPlainString());out.put("mean_precision","DECIMAL128");return out;}
        public Map<String,String> aggregate(String group,String value){int g=col(group),v=col(value);Map<String,BigDecimal> sums=new TreeMap<>();for(List<String> r:rows)sums.merge(r.get(g),decimal(r.get(v)),BigDecimal::add);Map<String,String> out=new LinkedHashMap<>();sums.forEach((k,n)->out.put(k,n.toPlainString()));return out;}
        public void validateNumeric(String column){int c=col(column);for(List<String> r:rows)decimal(r.get(c));}
        public String csv(){StringBuilder b=new StringBuilder();List<List<String>> all=new ArrayList<>();all.add(columns);all.addAll(rows);for(List<String> row:all){for(int i=0;i<row.size();i++){if(i>0)b.append(',');b.append('"').append(row.get(i).replace("\"","\"\"")).append('"');}b.append("\r\n");}return b.toString();}
        public String json(){JsonArray out=new JsonArray();for(List<String> row:rows){JsonObject obj=new JsonObject();for(int i=0;i<columns.size();i++)obj.addProperty(columns.get(i),row.get(i));out.add(obj);}return out.toString();}
    }
    private static BigDecimal decimal(String s){try{if(s.length()>100)throw new NumberFormatException();BigDecimal d=new BigDecimal(s);if(Math.abs((long)d.scale())>100)throw new NumberFormatException();return d;}catch(NumberFormatException e){throw new Failure("INVALID_NUMBER");}}
    public Table csv(String input){
        if(input==null||input.length()>2*1024*1024)throw new Failure("INPUT_TOO_LARGE");
        List<List<String>> records=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder field=new StringBuilder();boolean quoted=false,closed=false;
        for(int i=0;i<input.length();i++){char c=input.charAt(i);
            if(quoted){if(c=='"'){if(i+1<input.length()&&input.charAt(i+1)=='"'){field.append('"');i++;}else{quoted=false;closed=true;}}else field.append(c);}
            else if(c=='"'){if(field.length()!=0||closed)throw new Failure("INVALID_CSV");quoted=true;}
            else if(c==','||c=='\n'||c=='\r'){row.add(field.toString());field.setLength(0);closed=false;if(row.size()>100)throw new Failure("INVALID_TABLE");if(c!=','){records.add(row);row=new ArrayList<>();if(c=='\r'&&i+1<input.length()&&input.charAt(i+1)=='\n')i++;if(records.size()>MAX_ROWS+1)throw new Failure("INPUT_TOO_LARGE");}}
            else{if(closed)throw new Failure("INVALID_CSV");field.append(c);}
        }
        if(quoted)throw new Failure("INVALID_CSV");if(field.length()>0||closed||!row.isEmpty()){row.add(field.toString());records.add(row);}if(records.isEmpty())throw new Failure("INVALID_TABLE");return new Table(records.get(0),records.subList(1,records.size()),Arrays.asList("import_csv"));
    }
    public Table json(String input){if(input.length()>2*1024*1024)throw new Failure("INPUT_TOO_LARGE");JsonArray a=JsonParser.parseString(input).getAsJsonArray();if(a.isEmpty()||a.size()>MAX_ROWS)throw new Failure("INVALID_TABLE");List<String> cols=new ArrayList<>(a.get(0).getAsJsonObject().keySet());List<List<String>> rows=new ArrayList<>();for(JsonElement e:a){JsonObject o=e.getAsJsonObject();if(!o.keySet().equals(new HashSet<>(cols)))throw new Failure("INVALID_TABLE");List<String> r=new ArrayList<>();for(String c:cols){JsonElement v=o.get(c);if(!v.isJsonPrimitive())throw new Failure("INVALID_TABLE");r.add(v.getAsString());}rows.add(r);}return new Table(cols,rows,Arrays.asList("import_json"));}
}
