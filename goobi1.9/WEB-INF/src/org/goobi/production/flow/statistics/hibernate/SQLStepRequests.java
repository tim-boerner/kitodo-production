package org.goobi.production.flow.statistics.hibernate;
//TODO: Extract names of database fields
/**
 * This file is part of the Goobi Application - a Workflow tool for the support of 
 * mass digitization.
 * 
 * Visit the websites for more information. 
 *   - http://gdz.sub.uni-goettingen.de 
 *   - http://www.intranda.com 
 * 
 * Copyright 2009, Center for Retrospective Digitization, Göttingen (GDZ),
 * 
 * This program is free software; you can redistribute it and/or modify it under the 
 * terms of the GNU General Public License as published by the Free Software Foundation; 
 * either version 2 of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program; 
 * if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, 
 * Boston, MA 02111-1307 USA
 * 
 */

import java.util.Date;
import java.util.List;

import org.goobi.production.flow.statistics.enums.TimeUnit;

import de.sub.goobi.helper.enums.HistoryEventType;

/**
 * Class provides SQL for Step Requests statistics on the history table
 * it offers a little more functionallity compared to the other SQL Source 
 * classes. There are a little more parameters which can be set
 * 
 * @author Wulf Riebensahm
 *
 */
public class SQLStepRequests extends SQLGenerator {

	public SQLStepRequests(Date timeFrom, Date timeTo, TimeUnit timeUnit,
			List<Integer> ids) {
		// "history.processid - overrides the default value of prozesse.prozesseID
		// which is set in super class SQLGenerator
		super(timeFrom, timeTo, timeUnit, ids, "history.processID");
	}

	/** This is an extended SQL generator for an SQL extracting data from the historyEvent log.
	 *  depending on the parameters the query returns up to four fields 
	 * 
	 * (non-Javadoc)
	 * @see org.goobi.production.flow.statistics.hibernate.SQLGenerator#getSQL()
	 * 
	 * @param typeSelection - operates as additional filter
	 * @param stepOrder - operates as additional filter
	 * @param stepOrderGrouping - adding 'stepOrder' and 'stepName' fields in select and in group by clause 
	 * @param includeCorrections - adding additional stepOpen from Correction and other loops 
	 * 
	 * @return SQLExpression for MySQL DBMS - default fields stepCount and intervall
	 */
	public String getSQL(HistoryEventType typeSelection, Integer stepOrder,
			Boolean stepOrderGrouping, Boolean includeLoops) {

		String timeLimiter = "history.date" ;
		String groupInnerSelect = "";
		
		//evaluate if groupingFunction comes along with HistoryEventType
		// and if so implement this function in sql
		if (typeSelection.getGroupingFunction()!=null && !includeLoops){
				timeLimiter = typeSelection.getGroupingFunction() + "(history.date)";
				groupInnerSelect = " group by history.processid, history.numericvalue ";
		}
		
		String subQuery = "";
		String outerWhereClauseTimeFrame = getWhereClauseForTimeFrame(
				myTimeFrom, myTimeTo, "timeLimiter");
		String outerWhereClause = "";

		if (outerWhereClauseTimeFrame.length() > 0) {
			outerWhereClause = "WHERE " + outerWhereClauseTimeFrame;
		}

		//inner table -> alias "table_1"
		String innerWhereClause;

		if (myIdsCondition != null) {
			// adding ids to the where clause
			innerWhereClause = "(history.type="
					+ typeSelection.getValue().toString() + ")  AND ("
					+ myIdsCondition + ") ";
		} else {
			innerWhereClause = "(history.type="
					+ typeSelection.getValue().toString() + ") ";
		}

		// adding a stepOrder filter to numericvalue if parameter is set
		if (stepOrder != null) {
			innerWhereClause = innerWhereClause + " AND history.numericvalue="
					+ stepOrder.toString() + " ";
		}

		subQuery = "(SELECT numericvalue AS 'stepOrder', "
				+ getIntervallExpression(myTimeUnit, "history.date")
				+ " "
				+ "AS 'intervall', " + timeLimiter + " AS 'timeLimiter', history.stringvalue AS 'stepName' "
				+ "FROM history WHERE " + innerWhereClause + groupInnerSelect + ") AS table_1";

		mySql = "SELECT count(table_1.stepOrder) AS 'stepCount', table_1.intervall AS 'intervall' "
				+ addedListing(stepOrderGrouping)
				+ "FROM "
				+ subQuery
				+ " "
				+ outerWhereClause
				+ " GROUP BY table_1.intervall"
				+ addedGrouping(stepOrderGrouping)
				+ " ORDER BY  table_1.intervall"
				+ addedSorting(stepOrderGrouping);

		return mySql;
	}

	/** Method is purposfully not implemented. Method getSQL is overloaded   
	 *  with parametered method.
	 *   
	 * @see org.goobi.production.flow.statistics.hibernate.SQLGenerator#getSQL()
	 */
	@Override
	public String getSQL() {
		throw new UnsupportedOperationException(
				"The class "
						+ this.getClass().getName()
						+ " does not support the parameterless getSQL() method. Instead you need to use getSQL() with parameters.");
	}

	/** 
	 * 
	 * @param include
	 * @return SQL snippet for Order by clause
	 */

	private String addedSorting(Boolean include) {
		if (include) {
			return ", table_1.stepOrder";
		} else {
			return "";
		}
	}

	/** 
	 * 
	 * @param include
	 * @return SQL snippet for Select clause
	 */
	private String addedListing(Boolean include) {
		if (include) {
			return ", table_1.stepOrder, 'bogus' as 'stepName' ";
		} else {
			return "";
		}
	}

	/** 
	 * 
	 * @param include
	 * @return SQL snippet for Group by clause
	 */
	private String addedGrouping(Boolean include) {
		if (include) {
			return ", table_1.stepOrder ";
		} else {
			return "";
		}
	}

	/**
	 * 
	 * @param eventSelection
	 * @return SQL String to retrieve the highest numericvalue (stepOrder) for the event defined in eventSelection
	 */
	public String SQLMaxStepOrder(HistoryEventType eventSelection) {

		String timeRestriction;
		String innerWhereClause = null;
		if (myIdsCondition != null) {
			// adding ids to the where clause
			innerWhereClause = "(history.type="
					+ eventSelection.getValue().toString() + ")  AND ("
					+ myIdsCondition + ") ";
		} else {
			innerWhereClause = "(history.type="
					+ eventSelection.getValue().toString() + ") ";
		}

		timeRestriction = getWhereClauseForTimeFrame(myTimeFrom, myTimeTo,
				"history.date");

		if (timeRestriction.length() > 0) {
			innerWhereClause = innerWhereClause.concat(" AND "
					+ timeRestriction);
		}

		return "SELECT max(history.numericvalue) AS maxStep FROM history WHERE "
				+ innerWhereClause;
	}

	/**
	 * 
	 * @param eventSelection
	 * @return SQL String to retrieve the lowest numericvalue (stepOrder) for the event defined in eventSelection
	 */
	public String SQLMinStepOrder(HistoryEventType eventSelection) {

		String timeRestriction;
		String innerWhereClause = null;
		if (myIdsCondition != null) {
			// adding ids to the where clause
			innerWhereClause = "(history.type="
					+ eventSelection.getValue().toString() + ")  AND ("
					+ myIdsCondition + ") ";
		} else {
			innerWhereClause = "(history.type="
					+ eventSelection.getValue().toString() + ") ";
		}

		timeRestriction = getWhereClauseForTimeFrame(myTimeFrom, myTimeTo,
				"history.date");

		if (timeRestriction.length() > 0) {
			innerWhereClause = innerWhereClause.concat(" AND "
					+ timeRestriction);
		}

		return "SELECT min(history.numericvalue) AS minStep FROM history WHERE "
				+ innerWhereClause;
	}

}
