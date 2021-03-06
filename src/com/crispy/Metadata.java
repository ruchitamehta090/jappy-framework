package com.crispy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Metadata {
	String name;
	CopyOnWriteArrayList<Column> columns;
	CopyOnWriteArrayList<Index> indexes;
	CopyOnWriteArrayList<Constraint> constraints;
	Index primary;
	JSONObject comment;
	
	public Metadata(String table) {
		this.name = table;
		columns = new CopyOnWriteArrayList<Column>();
		indexes = new CopyOnWriteArrayList<Index>();
		constraints = new CopyOnWriteArrayList<Constraint>();
		comment = new JSONObject();
	}
	
	public Column getColumn(String name) {
		return Column.findByName(columns, name);
	}
	
	public Index getIndex(String name) {
		return Index.findByName(indexes, name);
	}
	
	public Constraint getConstraint(String column) {
		for (Constraint c : constraints) {
			if (c.sourceColumn.equals(column))
				return c;
		}
		return null;
	}
	
	public Index getPrimary() {
		return primary;
	}

	public JSONObject toJSONObject() throws JSONException {
		JSONObject o = new JSONObject();
		o.put("name", name);
		JSONArray columns = new JSONArray();
		for (Column c : this.columns) {
			columns.put(new JSONObject().put("name", c.name).put("type",
					c.type));
		}
		o.put("columns", columns);
		o.put("comment", comment);
		return o;
	}
	
	public String getTableName() {
		return name;
	}
	
	public String[] columnNames() {
		List<String> ret = new ArrayList<String>();
		for (Column c : columns) {
			ret.add(c.name);
		}
		return ret.toArray(new String[]{});
	}
	public CopyOnWriteArrayList<Column> getColumns() {
		return columns;
	}
	
	public void reorderAndRetain(final List<Column> refs) {
		ArrayList<Column> temp = new ArrayList<Column>(columns);
		
		// First get rid of all columns that we do not need
		Iterator<Column> iter = temp.iterator();
		while (iter.hasNext()) {
			Column col = iter.next();
			if (!refs.contains(col)) 
				iter.remove();
		}
		
		Collections.sort(temp, new Comparator<Column>() {
			@Override
			public int compare(Column o1, Column o2) {
				Integer o1i = refs.indexOf(o1);
				Integer o2i = refs.indexOf(o2);
				return o1i.compareTo(o2i);
			}
		});
		columns = new CopyOnWriteArrayList<Column>(temp);
	}
	
	public String getDisplay() {
		return comment.optString("display", null);
	}
	
	public boolean dataEntry() {
		if (name.startsWith("_"))
			return false;
		return !(comment.optBoolean("no-data-entry", false));
	}
	
	/**
	 * Admin columns must contain primary column.
	 * 
	 * @return
	 */
	public String[] adminColumns() {
		ArrayList<String> ret = new ArrayList<String>();
		final String primary = getPrimary().getColumn(0);
		ret.add(primary);
		String admin = comment.optString("admin", null);
		if (admin == null) {
			int c = 0;
			while (ret.size() < 5 && c < columns.size()) {
				if (columns.get(c).name.equals(primary)) {
					c++;
					continue;
				}
				ret.add(columns.get(c).name);
				c++;
			}
		} else {
			String[] admins = StringUtils.split(admin, ",");
			for (Column c : columns) {
				if (c.getName().equals(primary))
					continue;
				if (ArrayUtils.contains(admins, c.getName()))
					ret.add(c.getName());
			}
		}
		return ret.toArray(new String[]{});
	}
	
	public void setAdminColumns(String[] cols) throws Exception {
		comment.put("admin", StringUtils.join(cols, ","));
		Table.get("_metadata").columns("metadata").values(comment.toString()).where("table", name).update();
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(name);
		sb.append(" columns=" + columns.size());
		sb.append(" indexes=" + indexes.size());
		sb.append(" constraints=" + constraints.size());
		sb.append(" comment=" + comment.toString());
		return sb.toString();
	}

	public boolean isSystem() {
		return name.startsWith("_");
	}
}
