package de.openinc.ow.analytics.routes;

/**
 * Created by Martin on 18.10.2016.
 */
public class RemoteDataRoute {
	/*-
	private Map<String, Dataset> datasets;
	public RemoteDataRoute(Map<String,Dataset> current){
	datasets=current;
	}
	public Object handle(Request request, Response response) throws Exception {
	    Dataset current = datasets.getOrDefault(request.params("name"), new Dataset());
	    datasets.put(request.params("name"), current);
	    JSONObject resp = new JSONObject();
	    System.out.println(request.requestMethod());
	    int oldsize = current.size();
	    if(request.requestMethod().equals("POST")) {
	        current.clear();
	    }
	        try {
	            JSONObject options = new JSONObject(request.body());
	            int type=0;
	            if(options.optString("type").toLowerCase().equals("csv")){
	            //    type = RemoteDataAdapter.CSV;
	            }
	            if(options.optString("type").toLowerCase().equals("json")){
	              //  type = RemoteDataAdapter.JSON;
	            }
	            if(options.optString("type").toLowerCase().equals("tv_csv")){
	              //  type = RemoteDataAdapter.TV_CSV;
	            }
	           // RemoteDataAdapter rda = new RemoteDataAdapter(options.optString("location"), type);
	            
	            //Dataset newData = rda.getData();
	            Dataset newData = new Dataset();
	            current.addAll(newData);
	            resp.put("status", "OK");
	            resp.put("New Size", current.size());
	            resp.put("Old Size", oldsize);
	        } catch (Exception e) {
	            resp.put("status", "ERROR");
	            resp.put("cause", e.getCause());
	        }
	
	
	    if(datasets.get(request.params("name"))==null){
	        System.out.println("Does not exist");
	    }else{
	        System.out.println(datasets.get(request.params("name")).size());
	    }
	
	    return resp.toString();
	}
	*/
}
