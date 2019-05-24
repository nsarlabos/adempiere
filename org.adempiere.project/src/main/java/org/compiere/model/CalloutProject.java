/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package org.compiere.model;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.model.GridTabWrapper;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.TimeUtil;


/**
 *	Project Callouts
 *	
 *  @author Jorg Janke
 *  @version $Id: CalloutProject.java,v 1.3 2006/07/30 00:51:04 jjanke Exp $
 */
public class CalloutProject extends CalloutEngine
{
	/**
	 *	Project Planned - Price + Qty.
	 *		- called from PlannedPrice, PlannedQty
	 *		- calculates PlannedAmt (same as Trigger)
	 *  @param ctx context
	 *  @param WindowNo current Window No
	 *  @param mTab Grid Tab
	 *  @param mField Grid Field
	 *  @param value New Value
	 *  @return null or error message
	 */
	public  String planned (Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value)
	{
		if (isCalloutActive() || value == null)
			return "";

		BigDecimal PlannedQty, PlannedPrice;
		int StdPrecision = Env.getContextAsInt(ctx, WindowNo, "StdPrecision");


		//	get values
		PlannedQty = (BigDecimal)mTab.getValue("PlannedQty");
		if (PlannedQty == null)
			PlannedQty = Env.ONE;
		PlannedPrice = ((BigDecimal)mTab.getValue("PlannedPrice"));
		if (PlannedPrice == null)
			PlannedPrice = Env.ZERO;
		//
		BigDecimal PlannedAmt = PlannedQty.multiply(PlannedPrice);
		if (PlannedAmt.scale() > StdPrecision)
			PlannedAmt = PlannedAmt.setScale(StdPrecision, BigDecimal.ROUND_HALF_UP);
		//
		log.fine("PlannedQty=" + PlannedQty + " * PlannedPrice=" + PlannedPrice + " -> PlannedAmt=" + PlannedAmt + " (Precision=" + StdPrecision+ ")");
		mTab.setValue("PlannedAmt", PlannedAmt);
		return "";
	}	//	planned

	/**
	 * Fill Project Standard Phase
	 * @param ctx
	 * @param windowNo
	 * @param gridTab
	 * @param gridField
	 * @param value
	 * @return
	 */
	public String projectPhase(Properties ctx, int windowNo, GridTab gridTab, GridField gridField, Object value)
	{
		Optional<I_C_ProjectPhase> projectPhaseOptional = Optional.of(GridTabWrapper.create(gridTab, I_C_ProjectPhase.class));
		projectPhaseOptional.ifPresent(projectPhase -> {
			MProjectTypePhase projectTypePhase = (MProjectTypePhase) projectPhase.getC_Phase();
			if (projectPhase.getC_Phase_ID() > 0) {
				if (projectPhase.getName() == null || projectPhase.getName().isEmpty())
					projectPhase.setName(projectTypePhase.getName());
				if (projectPhase.getDescription() == null || projectPhase.getDescription().isEmpty())
					projectPhase.setDescription(projectTypePhase.getDescription());
				if (projectPhase.getHelp() == null || projectPhase.getHelp().isEmpty())
					projectPhase.setHelp(projectTypePhase.getHelp());
				if (projectPhase.getPriorityRule() == null || projectPhase.getPriorityRule().isEmpty())
					projectPhase.setPriorityRule(projectTypePhase.getPriorityRule());

				projectPhase.setIsMilestone(projectTypePhase.isMilestone());

				if(projectPhase.getDurationUnit() == null || projectPhase.getDurationUnit().isEmpty())
					projectPhase.setDurationUnit(projectTypePhase.getDurationUnit());
				if (projectPhase.getDurationEstimated().signum() == 0)
					projectPhase.setDurationEstimated(projectTypePhase.getDurationEstimated());

				if (projectPhase.getM_Product_ID() <=0 )
					projectPhase.setM_Product_ID(projectTypePhase.getM_Product_ID());

				if (projectPhase.getPP_Product_BOM_ID() <= 0)
					projectPhase.setPP_Product_BOM_ID(projectTypePhase.getPP_Product_BOM_ID());

				if (projectPhase.getAD_Workflow_ID() <= 0)
					projectPhase.setAD_Workflow_ID(projectTypePhase.getAD_Workflow_ID());

				if (projectPhase.getQty().signum() == 0)
					projectPhase.setQty(projectTypePhase.getStandardQty());

				projectPhase.setIsIndefinite(projectTypePhase.isIndefinite());
				projectPhase.setIsRecurrent(projectTypePhase.isRecurrent());
				projectPhase.setFrequencyType(projectTypePhase.getFrequencyType());
				projectPhase.setFrequency(projectTypePhase.getFrequency());
				projectPhase.setRunsMax(projectTypePhase.getRunsMax());
			}
		});
		return "";
	}

	/**
	 * Fill Project Task from Project Standard Task
	 * @param ctx
	 * @param windowNo
	 * @param gridTab
	 * @param gridField
	 * @param value
	 * @return
	 */
	public String projectTask(Properties ctx, int windowNo, GridTab gridTab, GridField gridField, Object value)
	{
		Optional<I_C_ProjectTask> projectTaskOptional = Optional.of(GridTabWrapper.create(gridTab, I_C_ProjectTask.class));
		projectTaskOptional.ifPresent(projectTask -> {
			MProjectTypeTask projectTypeTask = (MProjectTypeTask) projectTask.getC_Task();
			if (projectTask.getC_Task_ID() > 0) {
				if (projectTask.getName() == null || projectTask.getName().isEmpty())
					projectTask.setName(projectTypeTask.getName());
				if (projectTask.getDescription() == null || projectTask.getDescription().isEmpty())
					projectTask.setDescription(projectTypeTask.getDescription());
				if (projectTask.getHelp() == null || projectTask.getHelp().isEmpty())
					projectTask.setHelp(projectTypeTask.getHelp());
				if (projectTask.getPriorityRule() == null || projectTask.getPriorityRule().isEmpty())
					projectTask.setPriorityRule(projectTypeTask.getPriorityRule());

				projectTask.setIsMilestone(projectTypeTask.isMilestone());

				if (projectTask.getDurationUnit() == null || projectTask.getDurationUnit().isEmpty())
					projectTask.setDurationUnit(projectTypeTask.getDurationUnit());
				if (projectTask.getDurationEstimated().signum() == 0 )
					projectTask.setDurationEstimated(projectTypeTask.getDurationEstimated());
				if (projectTask.getM_Product_ID() <= 0)
					projectTask.setM_Product_ID(projectTypeTask.getM_Product_ID());

				if (projectTask.getPP_Product_BOM_ID() <= 0)
					projectTask.setPP_Product_BOM_ID(projectTypeTask.getPP_Product_BOM_ID());

				if (projectTask.getAD_Workflow_ID() <= 0)
					projectTask.setAD_Workflow_ID(projectTypeTask.getAD_Workflow_ID());

				projectTask.setIsIndefinite(projectTypeTask.isIndefinite());
				projectTask.setIsRecurrent(projectTypeTask.isRecurrent());
				projectTask.setFrequencyType(projectTypeTask.getFrequencyType());
				projectTask.setFrequency(projectTypeTask.getFrequency());
				projectTask.setRunsMax(projectTypeTask.getRunsMax());
			}
		});
		return "";
	}

	/**
	 * Complete percentage or complete flag
	 * @param ctx
	 * @param windowNo
	 * @param gridTab
	 * @param gridField
	 * @param value
	 * @return
	 */
	public String completeTask (Properties ctx, int windowNo, GridTab gridTab, GridField gridField, Object value)
	{
		Optional<I_C_ProjectTask> projectTaskOptional = Optional.of(GridTabWrapper.create(gridTab, I_C_ProjectTask.class));
		projectTaskOptional.ifPresent(projectTask -> {
			if (projectTask.getPercentageCompleted().compareTo( new BigDecimal(100)) == 0)
			{
				if (!projectTask.isComplete())
					projectTask.setIsComplete(true);
			}
			if (projectTask.isComplete())
			{
				if (projectTask.getPercentageCompleted().compareTo(new BigDecimal(100)) <= 0)
					projectTask.setPercentageCompleted(new BigDecimal(100));
			}

		});
		return null;
	}
	
	/**
	 *	Project Header - BPartner.
	 *		- M_PriceList_ID (+ Context)
	 *		- C_BPartner_Location_ID
	 *		- AD_User_ID
	 *		- POReference
	 *		- SO_Description
	 *		- PaymentRule
	 *		- C_PaymentTerm_ID
	 *  @param ctx      Context
	 *  @param WindowNo current Window No
	 *  @param mTab     Model Tab
	 *  @param mField   Model Field
	 *  @param value    The new value
	 *  @return Error message or ""
	 */
	public String bPartner (Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value)
	{
		Integer C_BPartner_ID = (Integer)value;
		if (C_BPartner_ID == null || C_BPartner_ID.intValue() == 0)
			return "";
		String sql = "SELECT p.AD_Language,p.C_PaymentTerm_ID,"
			+ " COALESCE(p.M_PriceList_ID,g.M_PriceList_ID) AS M_PriceList_ID, p.PaymentRule,p.POReference,"
			+ " p.SO_Description,p.IsDiscountPrinted,"
			+ " p.InvoiceRule,p.DeliveryRule,p.FreightCostRule,DeliveryViaRule,"
			+ " p.SO_CreditLimit, p.SO_CreditLimit-p.SO_CreditUsed AS CreditAvailable,"
			+ " lship.C_BPartner_Location_ID,c.AD_User_ID,"
			+ " COALESCE(p.PO_PriceList_ID,g.PO_PriceList_ID) AS PO_PriceList_ID, p.PaymentRulePO,p.PO_PaymentTerm_ID," 
			+ " lbill.C_BPartner_Location_ID AS Bill_Location_ID, p.SOCreditStatus, "
			+ " p.SalesRep_ID "
			+ "FROM C_BPartner p"
			+ " INNER JOIN C_BP_Group g ON (p.C_BP_Group_ID=g.C_BP_Group_ID)"			
			+ " LEFT OUTER JOIN C_BPartner_Location lbill ON (p.C_BPartner_ID=lbill.C_BPartner_ID AND lbill.IsBillTo='Y' AND lbill.IsActive='Y')"
			+ " LEFT OUTER JOIN C_BPartner_Location lship ON (p.C_BPartner_ID=lship.C_BPartner_ID AND lship.IsShipTo='Y' AND lship.IsActive='Y')"
			+ " LEFT OUTER JOIN AD_User c ON (p.C_BPartner_ID=c.C_BPartner_ID) "
			+ "WHERE p.C_BPartner_ID=? AND p.IsActive='Y'";		//	#1

		boolean IsSOTrx = "Y".equals(Env.getContext(ctx, WindowNo, "IsSOTrx"));
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, C_BPartner_ID.intValue());
			rs = pstmt.executeQuery();
			if (rs.next())
			{
				// Sales Rep - If BP has a default SalesRep then default it
				Integer salesRep = rs.getInt("SalesRep_ID");
				if (IsSOTrx && salesRep != 0 )
				{
					mTab.setValue("SalesRep_ID", salesRep);
				}
				Integer salesRepPO = rs.getInt("SalesRep_ID");
				if (!IsSOTrx && salesRepPO != 0 )
				{
					mTab.setValue("C_BPartnerSR_ID", salesRepPO);
				}
				
				
				//	PriceList (indirect: IsTaxIncluded & Currency)
				Integer ii = new Integer(rs.getInt(IsSOTrx ? "M_PriceList_ID" : "PO_PriceList_ID"));
				if (!rs.wasNull())
					mTab.setValue("M_PriceList_ID", ii);
				else
				{	//	get default PriceList
					int i = Env.getContextAsInt(ctx, "#M_PriceList_ID");
					if (i != 0)
						mTab.setValue("M_PriceList_ID", new Integer(i));
				}

				// Ship-To Location
				int shipTo_ID = rs.getInt("C_BPartner_Location_ID");
				//	overwritten by InfoBP selection - works only if InfoWindow
				//	was used otherwise creates error (uses last value, may belong to different BP)
				if (C_BPartner_ID.toString().equals(Env.getContext(ctx, WindowNo, Env.TAB_INFO, "C_BPartner_ID")))
				{
					String loc = Env.getContext(ctx, WindowNo, Env.TAB_INFO, "C_BPartner_Location_ID");
					if (loc.length() > 0)
						shipTo_ID = Integer.parseInt(loc);
				}
				if (shipTo_ID == 0)
					mTab.setValue("C_BPartner_Location_ID", null);
				else
					mTab.setValue("C_BPartner_Location_ID", new Integer(shipTo_ID));

				//	Contact - overwritten by InfoBP selection
				int contID = rs.getInt("AD_User_ID");
				if (C_BPartner_ID.toString().equals(Env.getContext(ctx, WindowNo, Env.TAB_INFO, "C_BPartner_ID")))
				{
					String cont = Env.getContext(ctx, WindowNo, Env.TAB_INFO, "AD_User_ID");
					if (cont.length() > 0)
						contID = Integer.parseInt(cont);
				}
				if (contID == 0)
					mTab.setValue("AD_User_ID", null);
				else
					mTab.setValue("AD_User_ID", new Integer(contID));


				//	PO Reference
				String s = rs.getString("POReference");
				if (s != null && s.length() != 0)
					mTab.setValue("POReference", s);
				// should not be reset to null if we entered already value! VHARCQ, accepted YS makes sense that way
				
				//	SO Description
				s = rs.getString("SO_Description");
				if (s != null && s.trim().length() != 0)
					mTab.setValue("Description", s);

				//	Defaults, if not Walkin Receipt or Walkin Invoice
				mTab.setValue("PaymentRule", X_C_Order.PAYMENTRULE_OnCredit);
			}
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql, e);
			return e.getLocalizedMessage();
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null; pstmt = null;
		}
		return "";
	}	//	bPartner
	
	/**
	 * Convert Quantity
	 * @param ctx
	 * @param windowNo
	 * @param tab
	 * @param field
	 * @param value
	 * @return
	 */
	public String quantity(Properties ctx, int windowNo, GridTab tab, GridField field, Object value) {
		if (isCalloutActive() || value == null)
			return "";
		//	Validate columns
		if(!field.getColumnName().equals("PlannedQty")
				&& !field.getColumnName().equals("Qty")
				&& !field.getColumnName().equals("QtyEntered")
				&& !field.getColumnName().equals("C_UOM_ID")
				&& !field.getColumnName().equals("M_Product_ID"))  {
			return "";
		}
		int uOmToId = Env.getContextAsInt(ctx, windowNo, "C_UOM_ID");
		//	get values
		int productId = (int) tab.getValue("M_Product_ID");
		if(productId <= 0) {
			return "";
		}
		BigDecimal quantityEntered = (BigDecimal) tab.getValue("QtyEntered");
		BigDecimal plannedQuantity = (BigDecimal) tab.getValue("PlannedQty");
		if(plannedQuantity == null) {
			plannedQuantity = (BigDecimal) tab.getValue("Qty");
		}
		if(quantityEntered == null) {
			quantityEntered = Env.ZERO;
		}
		if(plannedQuantity == null) {
			plannedQuantity = Env.ZERO;
		}
		log.fine("QtyEntered = " + quantityEntered + ", PlannedQty=" + plannedQuantity + ", UOM=" + uOmToId);
		//	Calculate
		if (field.getColumnName().equals("QtyEntered")
				|| field.getColumnName().equals("C_UOM_ID")
				|| field.getColumnName().equals("M_Product_ID")) {
			BigDecimal quantityEnteredRounded = quantityEntered.setScale(MUOM.getPrecision(ctx, uOmToId), BigDecimal.ROUND_HALF_UP);
			if (quantityEntered.compareTo(quantityEnteredRounded) != 0)
			{
				log.fine("Corrected QtyEntered Scale UOM=" + uOmToId 
					+ "; QtyEntered=" + quantityEntered + "->" + quantityEnteredRounded);  
				quantityEntered = quantityEnteredRounded;
				tab.setValue("QtyEntered", quantityEntered);
			}
			plannedQuantity = MUOMConversion.convertProductFrom (ctx, productId, uOmToId, quantityEntered);
			if (plannedQuantity == null) {
				plannedQuantity = quantityEntered;
			}
			boolean conversion = quantityEntered.compareTo(plannedQuantity) != 0;
			log.fine("UOM=" + uOmToId 
				+ ", QtyEntered=" + quantityEntered
				+ " -> " + conversion 
				+ " PlannedQty=" + plannedQuantity);
			tab.setValue("PlannedQty", plannedQuantity);
			tab.setValue("Qty", plannedQuantity);
		}
		//	PlannedQty changed - calculate QtyEntered (should not happen)
		else if (field.getColumnName().equals("PlannedQty")
				|| field.getColumnName().equals("Qty")) {
			int precision = MProduct.get(ctx, productId).getUOMPrecision(); 
			BigDecimal quantityRounded = plannedQuantity.setScale(precision, BigDecimal.ROUND_HALF_UP);
			if (plannedQuantity.compareTo(quantityRounded) != 0) {
				log.fine("Corrected PlannedQty Scale " 
					+ plannedQuantity + "->" + quantityRounded);  
				plannedQuantity = quantityRounded;
				tab.setValue("PlannedQty", plannedQuantity);
			}
			quantityEntered = MUOMConversion.convertProductTo (ctx, productId, uOmToId, plannedQuantity);
			if (quantityEntered == null) {
				quantityEntered = plannedQuantity;
			}
			boolean conversion = plannedQuantity.compareTo(quantityEntered) != 0;
			log.fine("UOM=" + uOmToId 
				+ ", PlannedQty=" + plannedQuantity
				+ " -> " + conversion 
				+ " QtyEntered=" + quantityEntered);
			tab.setValue("QtyEntered", quantityEntered);
		}
		//	
		if (plannedQuantity == null) {
			plannedQuantity = quantityEntered;
		}

		return "";
	}
}	//	CalloutProject
