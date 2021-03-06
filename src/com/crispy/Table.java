package com.crispy;

import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;

public class Table {
	public enum EngineType {
		MY_ISAM("MyISAM"), MEMORY("MEMORY"), HEAP("HEAP"), MERGE("MERGE"), MRG_MYISAM("MRG_MYISAM"), ISAM("ISAM"), MRG_ISAM("MRG_ISAM"), INNODB(
				"InnoDB"), INNOBASE("INNOBASE"), BDB("BDB"), BERKELEYDB("BERKELEYDB"), NDBCLUSTER("NDBCLUSTER"), NDB("NDB"), EXAMPLE("EXAMPLE"), ARCHIVE(
				"ARCHIVE"), CSV("CSV"), FEDERATED("FEDERATED"), BLACKHOLE("BLACKHOLE");

		String engineType;

		private EngineType(String type) {
			engineType = type;
		}

		public String getType() {
			return engineType;
		}
	}

	public enum WhereOp {
		EQUALS("="), NOT_EQUALS("!="), GREATER_THAN(">"), LESS_THAN("<"), LIKE(" LIKE "), GREATER_THAN_EQUALS(">="), LESS_THAN_EQUALS("<=");

		String sqlOp;

		private WhereOp(String op) {
			sqlOp = op;
		}

		public String sqlOp() {
			return sqlOp;
		}
	}

	public enum MatchMode {
		IN_BOOLEAN_MODE("IN BOOLEAN MODE"), IN_NATURAL_LANGUAGE_MODE("IN NATURAL LANGUAGE MODE"), WITH_QUERY_EXPANSION("WITH QUERY EXPANSION");

		String mode;

		private MatchMode(String mode) {
			this.mode = mode;
		}

		public String sqlMatchMode() {
			return mode;
		}
	}

	static class UpdateExp {
		String column;
		int amount;
	}

	static class WhereExp {
		String exp;
		Object values[];

		static WhereExp operator(String table, WhereOp op, String column, Object value) {
			WhereExp where = new WhereExp();
			where.exp = table + ".`" + column + "`" + op.sqlOp() + "?";
			where.values = new Object[1];
			where.values[0] = value;
			return where;
		}

		static WhereExp in(String table, String column, Object value[]) {
			WhereExp where = new WhereExp();
			where.exp = table + ".`" + column + "` IN (" + StringUtils.join(Collections.nCopies(value.length, "?"), ",") + ")";
			where.values = new Object[value.length];
			for (int i = 0; i < value.length; i++) {
				where.values[i] = value[i];
			}
			return where;
		}

		static WhereExp notIn(String table, String column, Object value[]) {
			WhereExp where = new WhereExp();
			where.exp = table + ".`" + column + "` NOT IN (" + StringUtils.join(Collections.nCopies(value.length, "?"), ",") + ")";
			where.values = new Object[value.length];
			for (int i = 0; i < value.length; i++) {
				where.values[i] = value[i];
			}
			return where;
		}

		static WhereExp matchAgainst(String table, String[] columns, String[] value, MatchMode mode) {
			WhereExp where = new WhereExp();
			StringBuilder match = new StringBuilder(table + ".`" + columns[0] + "`");
			for (int i = 1; i < columns.length; i++) {
				match.append("," + table + ".`" + columns[i] + "`");
			}
			where.exp = "MATCH (" + match.toString() + ") AGAINST (" + StringUtils.join(Collections.nCopies(value.length, "?"), " ") + " "
					+ mode.sqlMatchMode() + ")";
			where.values = new Object[value.length];
			for (int i = 0; i < value.length; i++) {
				where.values[i] = value[i];
			}
			return where;
		}
	}

	private static final Log LOG = Log.get("db");

	private ArrayList<Table> joins;

	private boolean random;
	private boolean unique;
	private String name;
	private ArrayList<WhereExp> where;
	// private HashMap<String, Pair<WhereOp, Object>> where;
	private ArrayList<String> columnNames;
	private ArrayList<String> columnNamesToSkip;
	private ArrayList<String> overwriteColumns;
	private ArrayList<Column> newColumns;
	private ArrayList<Index> newIndexes;
	private ArrayList<Constraint> newConstraints;
	private ArrayList<UpdateExp> increments;
	private Index newPrimaryKey;
	private Row copy;

	private ArrayList<Object> values;
	private boolean deleteOldColumns;

	private int limit;

	private String[] orderBy;

	private JSONObject comment;

	private String average;

	private boolean ignore;

	private int start;

	private boolean ignoreNull;

	private String groupBy;

	private EngineType engineType;

	private String max;

	public static Table get(String name) {
		Table t = new Table(name);
		return t;
	}

	public Table engine(EngineType type) {
		this.engineType = type;
		return this;
	}

	public Table overwrite(String... column) {
		this.overwriteColumns = new ArrayList<String>();
		this.overwriteColumns.addAll(Arrays.asList(column));
		return this;
	}

	private Table(String name) {
		deleteOldColumns = false;

		random = false;
		unique = false;
		ignore = false;
		limit = -1;
		start = -1;
		this.name = name;
		orderBy = new String[0];
		joins = new ArrayList<Table>();
		where = new ArrayList<Table.WhereExp>();
		joins.add(this);
		comment = new JSONObject();
		engineType = null;
	}

	public Table deleteOldColumns() {
		deleteOldColumns = true;
		return this;
	}

	public Table columns(String... name) {
		columnNames = new ArrayList<String>();
		columnNames.addAll(Arrays.asList(name));
		return this;
	}

	public Table skip(String... name) {
		columnNamesToSkip = new ArrayList<>();
		columnNamesToSkip.addAll(Arrays.asList(name));
		return this;
	}

	public Table copy(Row c) {
		this.copy = c;
		return this;
	}

	public Table columns(Column... c) {
		newColumns = new ArrayList<Column>();
		newColumns.addAll(Arrays.asList(c));
		for (Column cc : newColumns) {
			if (cc.autoIncrement) {
				newPrimaryKey = new Index(null, cc.name);
				newPrimaryKey.isAuto = true;
			}
		}
		return this;
	}

	public Table indexes(Index... i) {
		newIndexes = new ArrayList<Index>();
		newIndexes.addAll(Arrays.asList(i));
		return this;
	}

	public Table constraints(Constraint... c) {
		newConstraints = new ArrayList<Constraint>();
		newConstraints.addAll(Arrays.asList(c));
		for (Constraint nc : newConstraints)
			nc.sourceTable = name;
		return this;
	}

	public Table primary(String... name) {
		if (newPrimaryKey != null)
			throw new IllegalStateException("Already got one primary key");
		newPrimaryKey = new Index(null, name);
		return this;
	}

	public void create() {
		try {
			LOG.debug("create " + name);
			DB.loadMetadata(name);
			// If table does not exist.
			Metadata m = DB.getMetadata(name);
			if (m == null) {
				List<String> defs = new ArrayList<String>();
				for (Column column : newColumns) {
					defs.addAll(Arrays.asList(column.createDefinitions()));
				}
				DB.updateQuery("CREATE TABLE `" + name + "` (" + StringUtils.join(defs, ',') + ")");
				if (!name.equals("_metadata")) {
					Table.get("_metadata").columns("table", "metadata").values(name, comment.toString()).overwrite("metadata").add();
				}
			} else {
				for (Column column : newColumns) {
					Column oldColumn = Column.findByName(m.columns, column.name);
					if (oldColumn == null) {
						DB.updateQuery("ALTER TABLE `" + name + "` ADD COLUMN " + column.createDefinitions());
					} else {
						if (!oldColumn.equals(column)) {
							DB.updateQuery("ALTER TABLE `" + name + "` MODIFY COLUMN " + column.createDefinitions());
						}
					}
				}

				if (deleteOldColumns) {
					for (Column oldColumn : m.columns) {
						Column newColumn = Column.findByName(newColumns, oldColumn.name);
						if (newColumn == null) {
							DB.updateQuery("ALTER TABLE `" + name + "` DROP COLUMN `" + oldColumn.name + "`");
						}
					}
				}

				if (!m.comment.toString().equals(comment.toString())) {
					JSONObject merged = new JSONObject(m.comment.toString());
					Iterator<String> keys = comment.keys();
					while (keys.hasNext()) {
						String key = keys.next();
						merged.put(key, comment.get(key));
					}
					Table.get("_metadata").columns("table", "metadata").values(name, merged.toString()).overwrite("metadata").add();
				}
			}

			if (engineType != null) {
				DB.updateQuery("ALTER TABLE `" + name + "` ENGINE = " + engineType.getType());
			}
			if (newIndexes == null)
				newIndexes = new ArrayList<Index>();

			for (Index i : newIndexes) {
				Index oldIndex = (m == null) ? null : m.getIndex(i.name);
				if (oldIndex == null) {
					DB.updateQuery("ALTER TABLE `" + name + "` ADD " + i.createDefinition());
				} else if (!oldIndex.equals(i)) {
					DB.updateQuery("ALTER TABLE `" + name + "` DROP INDEX `" + oldIndex.name + "`");
					DB.updateQuery("ALTER TABLE `" + name + "` ADD " + i.createDefinition());
					LOG.info("CREATING NEW INDEX " + i.createDefinition());
				}
			}

			if (m != null) {
				for (Index i : m.indexes) {
					Index newIndex = Index.findByName(newIndexes, i.name);
					if (newIndex == null) {
						DB.updateQuery("ALTER TABLE `" + name + "` DROP INDEX `" + i.name + "`");
					}
				}
			}

			if (newConstraints == null)
				newConstraints = new ArrayList<Constraint>();

			for (Constraint c : newConstraints) {
				Constraint old = (m == null) ? null : m.getConstraint(c.sourceColumn);
				if (old == null) {
					c.create(name);
				} else if (!old.equals(c)) {
					old.drop();
					c.create(name);
				}
			}

			if (m != null) {
				for (Constraint c : m.constraints) {
					boolean found = false;
					for (Constraint newC : newConstraints) {
						if (newC.sourceColumn.equals(c.sourceColumn)) {
							found = true;
						}
					}
					if (!found) {
						c.drop();
					}
				}
			}

			if (newPrimaryKey != null) {
				Index oldPrimary = (m == null) ? null : m.primary;
				if (oldPrimary == null) {
					if (!newPrimaryKey.isAuto)
						DB.updateQuery("ALTER TABLE `" + name + "` ADD PRIMARY KEY " + newPrimaryKey.createDefinition());
				} else if (!oldPrimary.equals(newPrimaryKey)) {
					DB.updateQuery("ALTER TABLE `" + name + "` DROP PRIMARY KEY");
					if (!newPrimaryKey.isAuto)
						DB.updateQuery("ALTER TABLE `" + name + "` ADD PRIMARY KEY " + newPrimaryKey.createDefinition());
				}
			} else {
				if (m != null) {
					if (m.primary != null) {
						DB.updateQuery("ALTER TABLE `" + name + "` DROP PRIMARY KEY");
					}
				}
			}

			// Reload and reorder metadata
			DB.loadMetadata(name).reorderAndRetain(newColumns);
		} catch (Throwable t) {
			LOG.error(t.getMessage(), t);
			throw new IllegalStateException(t);
		}

	}

	public Table increment(String... columns) {
		if (this.increments == null) {
			this.increments = new ArrayList<Table.UpdateExp>();
		}
		for (String col : columns) {
			UpdateExp ue = new UpdateExp();
			ue.column = col;
			ue.amount = 1;
			this.increments.add(ue);
		}
		return this;
	}

	public Table add(String column, int value) {
		if (this.increments == null)
			this.increments = new ArrayList<Table.UpdateExp>();
		UpdateExp ue = new UpdateExp();
		ue.column = column;
		ue.amount = value;
		this.increments.add(ue);
		return this;
	}

	public Table decrement(String... columns) {
		if (this.increments == null) {
			this.increments = new ArrayList<Table.UpdateExp>();
		}
		for (String col : columns) {
			UpdateExp ue = new UpdateExp();
			ue.column = col;
			ue.amount = -1;
			this.increments.add(ue);
		}
		return this;
	}

	public Table values(Object... values) {
		if (this.values == null) {
			this.values = new ArrayList<Object>();
		}

		Metadata m = DB.getMetadata(name);

		for (int i = 0; i < values.length; i++) {
			String columnName = columnNames.get(i);
			Column c = Column.findByName(m.columns, columnName);
			if (c == null) {
				throw new IllegalStateException("Missing column " + columnName + " in table " + name);
			}
			this.values.add(c.parseObject(values[i]));
		}
		return this;
	}

	private static String safeJoin(List<String> e) {
		List<String> temp = new ArrayList<String>();
		for (String s : e) {
			temp.add("`" + s + "`");
		}
		return StringUtils.join(temp, ',');
	}

	public Table ignore() {
		ignore = true;
		return this;
	}

	public Table ignoreNull() {
		ignoreNull = true;
		return this;
	}

	public Table random() {
		this.random = true;
		return this;
	}

	public void update() {
		Connection con = DB.getConnection();
		try {
			StringBuilder sb = new StringBuilder();
			sb.append("UPDATE");
			if (ignore) {
				sb.append(" IGNORE");
			}
			sb.append(" `" + name + "` SET ");
			ArrayList<String> updates = new ArrayList<String>();
			if (columnNames != null) {
				for (int c = 0; c < columnNames.size(); c++) {
					if (!ignoreNull || values.get(c) != null)
						updates.add("`" + columnNames.get(c) + "`=?");
				}
			}
			if (increments != null) {
				for (UpdateExp ue : increments) {
					updates.add(String.format("`%s`=`%s`+?", ue.column, ue.column));
				}
			}
			sb.append(StringUtils.join(updates, ','));

			whereStatement(sb);
			LOG.info("update=" + sb.toString());
			PreparedStatement pstmt = con.prepareStatement(sb.toString());
			int c = 1;

			if (columnNames != null) {
				for (int v = 0; v < values.size(); v++) {
					if (!ignoreNull || values.get(v) != null)
						pstmt.setObject(c++, values.get(v));
				}
			}
			if (increments != null) {
				for (UpdateExp ue : increments) {
					pstmt.setInt(c++, ue.amount);
				}
			}

			whereValues(pstmt, c);

			pstmt.executeUpdate();
			pstmt.close();
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}

		finally {
			try {
				con.close();
			} catch (Exception e) {
			}
		}
	}

	private Object valueForColumn(String column) {
		int index = -1;
		if (columnNames != null) {
			index = columnNames.indexOf(column);
		}
		if (index == -1) {
			if (copy != null) {
				return copy.column(column);
			}
			throw new IllegalStateException("No Value supplied for the column=" + column);
		}
		return values.get(index);
	}

	public long add() throws Exception {
		Connection con = DB.getConnection();
		try {
			long genId = -1;

			List<String> myColumnNames = null;
			if (copy == null) {
				myColumnNames = columnNames;
			} else {
				myColumnNames = new ArrayList<>();
				for (Column c : DB.getMetadata(name).getColumns()) {
					if (c.autoIncrement)
						continue;
					myColumnNames.add(c.name);
				}
				if (columnNamesToSkip != null) {
					myColumnNames.removeAll(columnNamesToSkip);
				}
			}

			StringBuilder sb = new StringBuilder();
			sb.append("INSERT " + (((overwriteColumns != null) || ignore) ? "IGNORE " : "") + "INTO `" + name + "`(");
			sb.append(safeJoin(myColumnNames));
			sb.append(") VALUES (" + StringUtils.join(Collections.nCopies(myColumnNames.size(), "?"), ',') + ")");

			if (overwriteColumns != null && overwriteColumns.size() > 0) {
				sb.append(" ON DUPLICATE KEY UPDATE ");
				ArrayList<String> updates = new ArrayList<String>();
				for (String column : overwriteColumns) {
					if (!ignoreNull || valueForColumn(column) != null)
						updates.add("`" + column + "`=?");
				}
				sb.append(StringUtils.join(updates, ','));
			}

			PreparedStatement pstmt = con.prepareStatement(sb.toString(), Statement.RETURN_GENERATED_KEYS);

			int c = 1;
			for (String column : myColumnNames) {
				pstmt.setObject(c++, valueForColumn(column));
			}
			if (overwriteColumns != null && overwriteColumns.size() > 0) {
				for (String column : overwriteColumns) {
					if (!ignoreNull || valueForColumn(column) != null) {
						pstmt.setObject(c++, valueForColumn(column));
					}
				}
			}

			pstmt.executeUpdate();

			ResultSet generated = pstmt.getGeneratedKeys();
			if (generated.next()) {
				genId = generated.getLong(1);
			}

			pstmt.close();
			return genId;
		} finally {
			con.close();
		}
	}

	private PreparedStatement createJoinSelectstatement(Connection con, boolean count) throws SQLException {
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT ");

		if (!count) {
			ArrayList<String> names = new ArrayList<String>();
			for (Table t : joins) {
				if (t.columnNames == null) {
					Metadata m = DB.getMetadata(t.name);
					for (Column column : m.columns) {
						names.add("`" + t.name + "`.`" + column.name + "`");
					}
				} else {
					for (String columnName : t.columnNames) {
						names.add("`" + t.name + "`.`" + columnName + "`");
					}
				}
			}
			sb.append(StringUtils.join(names, ","));
		} else {
			sb.append("COUNT(*)");
		}

		sb.append(" FROM ");
		{
			ArrayList<String> tables = new ArrayList<String>();
			for (Table t : joins) {
				tables.add("`" + t.name + "`");
			}
			sb.append(StringUtils.join(tables, " JOIN "));
		}
		sb.append(" ON ");
		{
			ArrayList<String> joinCondition = new ArrayList<String>();
			for (int t = 0; t < joins.size() - 1; t++) {
				Table t1 = joins.get(t);
				Table t2 = joins.get(t + 1);
				Metadata m1 = DB.getMetadata(t1.name);
				Metadata m2 = DB.getMetadata(t2.name);

				Constraint c = Constraint.to(m1.constraints, t2.name);
				if (c == null)
					c = Constraint.to(m2.constraints, t1.name);

				joinCondition.add("`" + c.sourceTable + "`.`" + c.sourceColumn + "`=`" + c.destTable + "`.`" + c.destColumn + "`");
			}
			sb.append(StringUtils.join(joinCondition, ","));
		}

		ArrayList<String> items = new ArrayList<String>();
		for (Table t : joins) {
			if (t.where.size() > 0) {
				for (WhereExp exp : t.where) {
					items.add(exp.exp);
				}
			}
		}
		if (items.size() > 0) {
			sb.append(" WHERE " + StringUtils.join(items, " AND "));
		}

		for (Table t : joins) {
			if (t.groupBy != null) {
				sb.append(" GROUP BY `" + t.name + "`." + t.groupBy);
				break;
			}
		}

		for (Table t : joins) {
			if (t.orderBy.length > 0) {
				String[] temp = new String[t.orderBy.length];
				for (int i = 0; i < t.orderBy.length; i++) {
					temp[i] = "`" + t.name + "`." + t.orderBy[i];
				}
				sb.append(" ORDER BY " + StringUtils.join(temp, ","));
				break;
			}
		}

		if (limit != -1) {
			if (start != -1) {
				sb.append(" LIMIT " + start + "," + limit);
			} else {
				sb.append(" LIMIT " + limit);
			}
		}

		LOG.trace(sb.toString());
		PreparedStatement pstmt = con.prepareStatement(sb.toString());
		int c = 1;
		for (Table t : joins) {
			c = t.whereValues(pstmt, c);
			System.out.println(c + " " + t.where.size());
		}
		return pstmt;
	}

	@Override
	public String toString() {
		return name;
	}

	private PreparedStatement createSelectStatement(Connection con, boolean count) throws SQLException {
		if (joins.size() > 1)
			return createJoinSelectstatement(con, count);
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT ");

		if (unique)
			sb.append("DISTINCT ");

		if (columnNames != null) {
			ArrayList<String> names = new ArrayList<String>();
			for (String columnName : columnNames) {
				names.add("`" + columnName + "`");
			}
			sb.append(StringUtils.join(names, ','));
		} else {
			if (count) {
				sb.append("COUNT(*)");
			} else if (average != null) {
				sb.append("AVG(" + average + ")");
			} else if (max != null) {
				sb.append("MAX(" + max + ")");
			} else {
				sb.append("*");
			}
		}
		sb.append(" FROM `" + name + "`");
		whereStatement(sb);
		if (groupBy != null) {
			sb.append(" GROUP BY `" + groupBy + "`");
		}

		if (orderBy.length > 0) {
			sb.append(" ORDER BY " + StringUtils.join(orderBy, ","));
		}
		if (orderBy.length == 0 && random) {
			sb.append(" ORDER BY RAND()");
		}
		if (limit != -1) {
			if (start != -1) {
				sb.append(" LIMIT " + start + "," + limit);
			} else {
				sb.append(" LIMIT " + limit);
			}
		}

		LOG.trace(sb.toString());
		PreparedStatement pstmt = con.prepareStatement(sb.toString());
		int c = 1;
		whereValues(pstmt, c);
		return pstmt;
	}

	public Row row() {
		Connection con = DB.getConnection();
		try {
			PreparedStatement pstmt = createSelectStatement(con, false);
			Row ret = null;
			ResultSet results = pstmt.executeQuery();
			if (results.next())
				ret = new Row(results);

			pstmt.close();

			return ret;
		} catch (Throwable t) {
			LOG.error(t.getMessage(), t);
			throw new IllegalStateException(t);
		} finally {
			try {
				con.close();
			} catch (Throwable t) {
			}
		}
	}

	public void rows(RowCallback callback) {
		Connection con = DB.getConnection();
		try {
			PreparedStatement pstmt = createSelectStatement(con, false);
			pstmt.setFetchSize(Integer.MIN_VALUE);
			ResultSet results = pstmt.executeQuery();
			while (results.next()) {
				callback.row(new Row(results));
			}
			pstmt.close();
		} catch (Throwable t) {
			LOG.error(t.getMessage(), t);
			throw new IllegalStateException(t);
		} finally {
			try {
				con.close();
			} catch (Throwable t) {
			}
		}
	}

	public void lock() {
		Connection con = DB.getConnection();
		try {
			PreparedStatement pstmt = con.prepareStatement("LOCK TABLE ?");
			pstmt.setString(1, name);
			pstmt.executeQuery();
		} catch (Throwable t) {
			LOG.error(t.getMessage(), t);
		} finally {
			try {
				con.close();
			} catch (Throwable t) {
			}
		}
	}

	public void unlock() {
		Connection con = DB.getConnection();
		try {
			PreparedStatement pstmt = con.prepareStatement("UNLOCK TABLE ?");
			pstmt.setString(1, name);
			pstmt.executeQuery();
		} catch (Throwable t) {
			LOG.error(t.getMessage(), t);
		} finally {
			try {
				con.close();
			} catch (Throwable t) {
			}
		}
	}

	public String[] strings() {
		if (columnNames.size() != 1)
			throw new IllegalArgumentException("Only one column can be returned as array");
		List<Row> rows = rows();
		String[] ret = new String[rows.size()];
		for (int i = 0; i < rows.size(); i++) {
			ret[i] = rows.get(i).string(columnNames.get(0));
		}
		return ret;
	}

	public List<Row> rows() {
		Connection con = DB.getConnection();
		try {
			PreparedStatement pstmt = createSelectStatement(con, false);

			List<Row> ret = new ArrayList<Row>();
			ResultSet results = pstmt.executeQuery();
			while (results.next()) {
				ret.add(new Row(results));
			}

			pstmt.close();
			return ret;
		} catch (Throwable t) {
			LOG.error(t.getMessage(), t);
			throw new IllegalStateException(t);
		} finally {
			try {
				con.close();
			} catch (Throwable t) {
			}
		}
	}

	public <T> List<T> customRows(Class<T> c) {
		Connection con = DB.getConnection();
		try {
			PreparedStatement pstmt = createSelectStatement(con, false);

			List<T> ret = new ArrayList<T>();
			ResultSet results = pstmt.executeQuery();
			Constructor<T> cons = c.getConstructor(ResultSet.class);
			while (results.next()) {
				ret.add(cons.newInstance(results));
			}
			pstmt.close();
			return ret;
		} catch (Throwable t) {
			LOG.error(t.getMessage(), t);
			throw new IllegalStateException(t);
		} finally {
			try {
				con.close();
			} catch (Throwable t) {
			}
		}
	}

	public Table where(String column, Object value) {
		return where(column, value, WhereOp.EQUALS);
	}

	public Table notIn(String column, Object value[]) {
		Metadata m = DB.getMetadata(name);
		Column c = Column.findByName(m.columns, column);
		if (c == null) {
			throw new IllegalStateException("No column exists for " + column + " in table " + name);
		}
		Object[] parsed = new Object[value.length];
		for (int i = 0; i < value.length; i++) {
			parsed[i] = c.parseObject(value[i]);
		}
		where.add(WhereExp.notIn(name, column, parsed));
		return this;
	}

	public Table in(String column, Object value[]) {
		Metadata m = DB.getMetadata(name);
		Column c = Column.findByName(m.columns, column);
		if (c == null) {
			throw new IllegalStateException("No column exists for " + column + " in table " + name);
		}
		Object[] parsed = new Object[value.length];
		for (int i = 0; i < value.length; i++) {
			parsed[i] = c.parseObject(value[i]);
		}
		where.add(WhereExp.in(name, column, parsed));
		return this;
	}

	public Table search(String[] columns, String query, MatchMode mode) {
		Metadata m = DB.getMetadata(name);
		if (columns.length == 0) {
			throw new IllegalStateException("Not a single column available to match");
		}
		for (int i = 0; i < columns.length; i++) {
			Column c = Column.findByName(m.columns, columns[i]);
			if (c == null) {
				throw new IllegalStateException("No column exists for " + c + " in table " + name);
			}
		}
		if (query == null || query.equals("")) {
			throw new IllegalStateException("No keyword available to match against");
		}
		if (mode == null) {
			mode = MatchMode.IN_NATURAL_LANGUAGE_MODE;
		}
		where.add(WhereExp.matchAgainst(name, columns, new String[] { query }, mode));
		return this;
	}

	public Table where(String column, Object value, WhereOp op) {
		Metadata m = DB.getMetadata(name);
		if (m == null)
			throw new IllegalStateException("No table exists for " + name);
		Column c = Column.findByName(m.columns, column);
		if (c == null) {
			throw new IllegalStateException("No column exists for " + column + " in table " + name);
		}
		where.add(WhereExp.operator(name, op, column, c.parseObject(value)));
		return this;
	}

	public double average(String column) throws SQLException {
		average = column;
		Connection con = DB.getConnection();
		try {
			PreparedStatement pstmt = createSelectStatement(con, false);
			ResultSet results = pstmt.executeQuery();
			if (results.next())
				return results.getDouble(1);
			return 0;
		} catch (Throwable t) {
			LOG.error(t.getMessage(), t);
			throw new IllegalStateException(t);
		} finally {
			con.close();
		}
	}

	public long max(String column) throws SQLException {
		max = column;
		Connection con = DB.getConnection();
		try {
			PreparedStatement pstmt = createSelectStatement(con, false);
			ResultSet results = pstmt.executeQuery();
			if (results.next())
				return results.getLong(1);
			return 0;
		} catch (Throwable t) {
			LOG.error(t.getMessage(), t);
			throw new IllegalStateException(t);
		} finally {
			con.close();
		}
	}

	public long count() throws SQLException {
		Connection con = DB.getConnection();
		try {
			PreparedStatement pstmt = createSelectStatement(con, true);
			ResultSet results = pstmt.executeQuery();
			if (results.next())
				return results.getLong(1);
			return 0;
		} catch (Throwable t) {
			LOG.error(t.getMessage(), t);
			throw new IllegalStateException(t);
		} finally {
			con.close();
		}
	}

	public Table limit(int l) {
		limit = l;
		return this;
	}

	public Table lastNDays(String column, int N) {
		WhereExp exp = new WhereExp();
		exp.exp = "(DATE(`" + column + "`)<=CURRENT_DATE() AND DATE(`" + column + "`)>=DATE_SUB(CURRENT_DATE(), INTERVAL " + N + " DAY))";
		exp.values = new Object[0];
		where.add(exp);
		return this;
	}

	public Table nextNDays(String column, int N) {
		WhereExp exp = new WhereExp();
		exp.exp = "(DATE(`" + column + "`)>CURRENT_DATE() AND DATE(`" + column + "`)<=DATE_ADD(CURRENT_DATE(), INTERVAL " + N + " DAY))";
		exp.values = new Object[0];
		where.add(exp);
		return this;
	}

	public void drop(boolean ignore) {
		try {
			DB.updateQuery("DROP TABLE `" + name + "`");
			DB.loadMetadata(name);
		} catch (Throwable e) {
			if (!ignore)
				throw new IllegalStateException(e);
		}
	}

	public Table ascending(String column) {
		orderBy = (String[]) ArrayUtils.add(orderBy, "`" + column + "` ASC");
		return this;
	}

	public Table descending(String column) {
		orderBy = (String[]) ArrayUtils.add(orderBy, "`" + column + "` DESC");
		return this;
	}

	public void delete() throws SQLException {
		StringBuilder sb = new StringBuilder();
		sb.append("DELETE FROM `" + name + "`");
		whereStatement(sb);
		Connection con = DB.getConnection();
		try {
			PreparedStatement pstmt = con.prepareStatement(sb.toString());
			int c = 1;
			whereValues(pstmt, c);
			pstmt.executeUpdate();
			pstmt.close();
		} finally {
			con.close();
		}
	}

	private void whereStatement(StringBuilder sb) {
		if (where.size() > 0) {
			sb.append(" WHERE ");
			ArrayList<String> items = new ArrayList<String>();
			for (WhereExp exp : where) {
				items.add(exp.exp);
			}
			sb.append(StringUtils.join(items, " AND "));
		}
	}

	private int whereValues(PreparedStatement pstmt, int ctr) throws SQLException {
		for (WhereExp exp : where) {
			for (Object value : exp.values) {
				pstmt.setObject(ctr++, value);
			}
		}
		return ctr;
	}

	public Table display(String column) throws JSONException {
		comment.put("display", column);
		return this;
	}

	public Table noDataEntry() throws JSONException {
		comment.put("no-data-entry", true);
		return this;
	}

	public Table join(Table t) {
		joins.add(t);
		return this;
	}

	public Table groupBy(String column) {
		this.groupBy = column;
		return this;
	}

	public Table greater(String column, Object value) {
		return where(column, value, WhereOp.GREATER_THAN);
	}

	public Table isNull(String column) {
		WhereExp exp = new WhereExp();
		exp.exp = "ISNULL(`" + column + "`)";
		exp.values = new Object[0];
		where.add(exp);
		return this;
	}

	public Table unique() {
		this.unique = true;
		return this;
	}

	public Table isNotNull(String column) {
		WhereExp exp = new WhereExp();
		exp.exp = "!ISNULL(`" + column + "`)";
		exp.values = new Object[0];
		where.add(exp);
		return this;
	}

	public Table start(int s) {
		start = s;
		if (limit == -1)
			limit = 10;
		return this;
	}

}
