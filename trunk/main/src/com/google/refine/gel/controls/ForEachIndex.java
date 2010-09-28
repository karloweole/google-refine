package com.google.refine.gel.controls;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONWriter;

import com.google.refine.expr.EvalError;
import com.google.refine.expr.Evaluable;
import com.google.refine.expr.ExpressionUtils;
import com.google.refine.gel.Control;
import com.google.refine.gel.ControlFunctionRegistry;
import com.google.refine.gel.ast.VariableExpr;

public class ForEachIndex implements Control {
    public String checkArguments(Evaluable[] args) {
        if (args.length != 4) {
            return ControlFunctionRegistry.getControlName(this) + " expects 4 arguments";
        } else if (!(args[1] instanceof VariableExpr)) {
            return ControlFunctionRegistry.getControlName(this) + 
                " expects second argument to be the index's variable name";
        } else if (!(args[2] instanceof VariableExpr)) {
            return ControlFunctionRegistry.getControlName(this) + 
                " expects third argument to be the element's variable name";
        }
        return null;
    }

    public Object call(Properties bindings, Evaluable[] args) {
        Object o = args[0].evaluate(bindings);
        if (ExpressionUtils.isError(o)) {
            return o;
        } else if (!ExpressionUtils.isArrayOrCollection(o) && !(o instanceof JSONArray)) {
            return new EvalError("First argument to forEach is not an array");
        }
        
        String indexName = ((VariableExpr) args[1]).getName();
        String elementName = ((VariableExpr) args[2]).getName();
        
        Object oldIndexValue = bindings.get(indexName);
        Object oldElementValue = bindings.get(elementName);
        try {
            List<Object> results = null;
            
            if (o.getClass().isArray()) {
                Object[] values = (Object[]) o;
                
                results = new ArrayList<Object>(values.length);
                
                for (int i = 0; i < values.length; i++) {
                    Object v = values[i];
                    
                    bindings.put(indexName, i);
                    bindings.put(elementName, v);
                    
                    Object r = args[3].evaluate(bindings);
                    
                    results.add(r);
                }
            } else if (o instanceof JSONArray) {
                JSONArray a = (JSONArray) o;
                int l = a.length();
                
                results = new ArrayList<Object>(l);
                for (int i = 0; i < l; i++) {
                    try {
                        Object v = a.get(i);
                        
                        bindings.put(indexName, i);
                        bindings.put(elementName, v);
                        
                        Object r = args[3].evaluate(bindings);
                        
                        results.add(r);
                    } catch (JSONException e) {
                        results.add(new EvalError(e.getMessage()));
                    }
                }
            } else {
                List<Object> list = ExpressionUtils.toObjectList(o);
                
                results = new ArrayList<Object>(list.size());
                
                for (int i = 0; i < list.size(); i++) {
                	Object v = list.get(i);
                	
                	bindings.put(indexName, i);
                    bindings.put(elementName, v);
                    
                    Object r = args[3].evaluate(bindings);
                    
                    results.add(r);
                }
            }
            
            return results.toArray(); 
        } finally {
            /*
             *  Restore the old values bound to the variables, if any.
             */
            if (oldIndexValue != null) {
                bindings.put(indexName, oldIndexValue);
            } else {
                bindings.remove(indexName);
            }
            if (oldElementValue != null) {
                bindings.put(elementName, oldElementValue);
            } else {
                bindings.remove(elementName);
            }
        }
    }
    
    public void write(JSONWriter writer, Properties options)
        throws JSONException {
    
        writer.object();
        writer.key("description"); writer.value(
            "Evaluates expression a to an array. Then for each array element, binds its index to variable i and its value to variable name v, evaluates expression e, and pushes the result onto the result array."
        );
        writer.key("params"); writer.value("expression a, variable i, variable v, expression e");
        writer.key("returns"); writer.value("array");
        writer.endObject();
    }
}