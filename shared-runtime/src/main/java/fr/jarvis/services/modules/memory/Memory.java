package fr.jarvis.services.modules.memory;

import java.util.*;
import com.google.gson.Gson;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.shared.storage.Store;
import fr.jarvis.services.shared.api.Contracts.Failure;

public final class Memory {
    private final Store store;private final Guard guard;private final Map<String,String> temporary=new HashMap<>();
    public Memory(Store store,Guard guard){this.store=store;this.guard=guard;}
    public void temporary(Context ctx,String key,String text){guard.require(ctx,"memory.write");temporary.put(key,text);}
    public String temporary(Context ctx,String key){guard.require(ctx,"memory.read");return temporary.get(key);}
    public String exact(String id,String type,String text,String source,long expires){return new Gson().toJson(Arrays.asList(id,type,text,source,expires));}
    public Map<String,Object> save(Context ctx,String id,String type,String text,String source,long expires,String token){
        if(!Arrays.asList("project","task","decision","preference").contains(type)||id==null||text==null||text.length()>10000||source==null||source.isEmpty()||expires<=System.currentTimeMillis())throw new Failure("INVALID_MEMORY");
        guard.consume(ctx,"memory.write",exact(id,type,text,source,expires),token);
        Map<String,Object> old=store.get("memory",id), row=new LinkedHashMap<>();row.put("id",id);row.put("type",type);row.put("text",text);row.put("source",source);row.put("expires_at",expires);row.put("updated_at",System.currentTimeMillis());row.put("version",old==null?1:((Number)old.get("version")).intValue()+1);store.put("memory",id,row);return row;
    }
    public List<Map<String,Object>> search(Context ctx,String query){guard.require(ctx,"memory.read");List<Map<String,Object>> out=new ArrayList<>();for(Map<String,Object> r:store.all("memory"))if(((Number)r.get("expires_at")).longValue()>System.currentTimeMillis()&&((String)r.get("text")).contains(query))out.add(r);return out;}
    public void delete(Context ctx,String id,String token){guard.consume(ctx,"memory.write",id,token);store.delete("memory",id);}
    public int purgeExpired(Context ctx){guard.require(ctx,"memory.write");int count=0;for(Map<String,Object> r:store.all("memory"))if(((Number)r.get("expires_at")).longValue()<=System.currentTimeMillis()){store.delete("memory",(String)r.get("id"));count++;}return count;}
    public String export(Context ctx){return new Gson().toJson(search(ctx,""));}
}
