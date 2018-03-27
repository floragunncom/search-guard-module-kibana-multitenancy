package com.floragunn.dlic.auth.http.jwt.keybyoidc;

import org.apache.cxf.jaxrs.json.basic.JsonMapObject;
import org.apache.cxf.jaxrs.json.basic.JsonMapObjectReaderWriter;

class CxfTestTools {

	static String toJson(JsonMapObject jsonMapObject) {
		return new JsonMapObjectReaderWriter().toJson(jsonMapObject);
	}
	
	

}
