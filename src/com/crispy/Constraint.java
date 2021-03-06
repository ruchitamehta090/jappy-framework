package com.crispy;

import java.sql.SQLException;
import java.util.List;

import com.google.common.base.Objects;

public class Constraint {
	String sourceTable;
	String sourceColumn;
	String destTable;
	String destColumn;

	public static Constraint create(String column, String foreignTable,
			String foreignColumn) {
		Constraint c = new Constraint();
		c.sourceColumn = column;
		c.destTable = foreignTable;
		c.destColumn = foreignColumn;
		return c;
	}

	@Override
	public boolean equals(Object obj) {
		Constraint o = (Constraint) obj;
		return Objects.equal(sourceTable, o.sourceTable)
				&& Objects.equal(sourceColumn, o.sourceColumn)
				&& Objects.equal(destTable, o.destTable)
				&& Objects.equal(destColumn, o.destColumn);
	}

	public void create(String table) throws SQLException {
		sourceTable = table;
		DB.updateQuery("ALTER TABLE `" + sourceTable + "` ADD CONSTRAINT `"
				+ sourceTable + "_" + sourceColumn + "` FOREIGN KEY `"
				+ sourceTable + "_" + sourceColumn + "`(`" + sourceColumn
				+ "`) REFERENCES `" + destTable + "`(`" + destColumn + "`)");
	}

	public void drop() throws SQLException {
		DB.updateQuery("ALTER TABLE `" + sourceTable + "` DROP FOREIGN KEY `"
				+ sourceTable + "_" + sourceColumn + "`");
		DB.updateQuery("ALTER TABLE `" + sourceTable + "` DROP INDEX `"
				+ sourceTable + "_" + sourceColumn + "`");
	}

	public static Constraint to(List<Constraint> constraints, String table) {
		for (Constraint c : constraints)
			if (c.destTable.equals(table))
				return c;
		return null;
	}

	public String getDestTable() {
		return destTable;
	}

	public String getDestColumn() {
		return destColumn;
	}
}
