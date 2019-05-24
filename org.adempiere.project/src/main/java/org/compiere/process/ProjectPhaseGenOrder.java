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
package org.compiere.process;


import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.*;
import org.compiere.util.Env;
import org.compiere.util.Util;


/**
 *  Generate Order from Project Phase
 *
 *	@author Jorg Janke
 *	@version $Id: ProjectPhaseGenOrder.java,v 1.2 2006/07/30 00:51:01 jjanke Exp $
 */
public class ProjectPhaseGenOrder  extends ProjectPhaseGenOrderAbstract
{
	/**
	 *  Prepare - e.g., get Parameters.
	 */
	protected void prepare()
	{
		super.prepare();
	}	//	prepare

	/**
	 *  Perform process.
	 *  @return Message (clear text)
	 *  @throws Exception if not successful
	 */
	protected String doIt() throws Exception
	{
		int documentTypeTargetId = getParameterAsInt(I_C_Order.COLUMNNAME_C_DocType_ID);
		Timestamp dateOrdered = getParameterAsTimestamp(I_C_Order.COLUMNNAME_DateOrdered);
		int projectId = 0;
		int projectPhaseId = 0;
		int projectTaskId = 0;
		String name = null;
		int productId = 0;
		int seqNo = 0;
		String description = null;
		BigDecimal quantityToOrder = null;
		BigDecimal priceActual = null;
		BigDecimal quantityEntered = null;
		int projectUomId = 0;
		
		
		List<MProjectLine> projectLines = null;
		List<MProjectTask> tasks = null;
		log.info("doIt - C_ProjectPhase_ID=" + getRecord_ID());
		if (getRecord_ID() == 0)
			throw new IllegalArgumentException("C_ProjectPhase_ID == 0");
		if (getDocSubTypeSO()==null)
			throw new AdempiereException("@NotFound@ @DocSubTypeSO@");
		if(MProjectTask.Table_Name.equals(getTableName())) {
			MProjectTask projectTask = new MProjectTask (getCtx(), getRecord_ID(), get_TrxName());
			projectPhaseId = projectTask.getC_ProjectPhase_ID();
			projectTaskId = projectTask.getC_ProjectTask_ID();
			MProjectPhase projectPhase = new MProjectPhase(getCtx(), projectPhaseId, get_TrxName());
			projectId = projectPhase.getC_Project_ID();
			name = projectTask.getName();
			productId = projectTask.getM_Product_ID() | 0;
			seqNo = projectTask.getSeqNo();
			description = projectTask.getDescription();
			quantityToOrder = projectTask.getQty();
			priceActual = projectTask.getPlannedAmt();
			//	Add UOM
			quantityEntered = (BigDecimal) projectTask.get_Value("QtyEntered");
			projectUomId = projectTask.get_ValueAsInt("C_UOM_ID");
		
			projectLines = Arrays.asList(projectTask.getLines());
		}
		else if(MProjectPhase.Table_Name.equals(getTableName())) {
			MProjectPhase projectPhase = new MProjectPhase (getCtx(), getRecord_ID(), get_TrxName());
			projectId = projectPhase.getC_Project_ID();
			projectPhaseId = projectPhase.getC_ProjectPhase_ID();
			name = projectPhase.getName();
			productId = projectPhase.getM_Product_ID() | 0;
			seqNo = projectPhase.getSeqNo();
			description = projectPhase.getDescription();
			quantityToOrder = projectPhase.getQty();
			priceActual = projectPhase.getPlannedAmt();
			projectLines = projectPhase.getLines();
			//	Add UOM
			quantityEntered = (BigDecimal) projectPhase.get_Value("QtyEntered");
			projectUomId = projectPhase.get_ValueAsInt("C_UOM_ID");
			tasks = projectPhase.getTasks();
		}
		
		MProject fromProject = ProjectGenOrder.getProject (getCtx(), projectId, get_TrxName());
		if (fromProject.getC_PaymentTerm_ID() <= 0)
			throw new AdempiereException("@C_PaymentTerm_ID@ @NotFound@");

		MOrder order = new MOrder (fromProject, true, getDocSubTypeSO());
		//	Add Document Type Target
		if(documentTypeTargetId > 0) {
			order.setC_DocTypeTarget_ID(documentTypeTargetId);
		}
		if(projectId > 0) {
			order.setC_Project_ID(projectId);
		}
		//	Phase
		if(projectPhaseId > 0) {
			order.set_ValueOfColumn("C_ProjectPhase_ID", projectPhaseId);
			MProjectPhase phase = new MProjectPhase(getCtx(), projectPhaseId, get_TrxName());
			if(phase.get_ValueAsBoolean(I_C_Order.COLUMNNAME_IsDropShip)) {
				int dropShipBPartnerId = phase.get_ValueAsInt(I_C_Order.COLUMNNAME_DropShip_BPartner_ID);
				int dropShipBPartnerLocationId = phase.get_ValueAsInt(I_C_Order.COLUMNNAME_DropShip_Location_ID);
				if(dropShipBPartnerId > 0
						&& dropShipBPartnerLocationId > 0) {
					order.setIsDropShip(phase.get_ValueAsBoolean(I_C_Order.COLUMNNAME_IsDropShip));
					order.setDropShip_BPartner_ID(dropShipBPartnerId);
					order.setDropShip_Location_ID(dropShipBPartnerLocationId);
				}
			}
		}
		//	Task
		if(projectTaskId > 0) {
			order.set_ValueOfColumn("C_ProjectTask_ID", projectTaskId);
			MProjectTask task = new MProjectTask(getCtx(), projectTaskId, get_TrxName());
			if(task.get_ValueAsBoolean(I_C_Order.COLUMNNAME_IsDropShip)) {
				int dropShipBPartnerId = task.get_ValueAsInt(I_C_Order.COLUMNNAME_DropShip_BPartner_ID);
				int dropShipBPartnerLocationId = task.get_ValueAsInt(I_C_Order.COLUMNNAME_DropShip_Location_ID);
				if(dropShipBPartnerId > 0
						&& dropShipBPartnerLocationId > 0) {
					order.setIsDropShip(task.get_ValueAsBoolean(I_C_Order.COLUMNNAME_IsDropShip));
					order.setDropShip_BPartner_ID(dropShipBPartnerId);
					order.setDropShip_Location_ID(dropShipBPartnerLocationId);
				}
			}
		}
		if(dateOrdered != null) {
			order.setDateOrdered(dateOrdered);
		}
		order.setDescription(order.getDescription() + " - " + name);
		order.setC_ConversionType_ID(MConversionType.TYPE_SPOT);
		order.saveEx();
		
		//	Create an order on Phase Level
		if (productId != 0) {
			MProduct product = new MProduct(getCtx(), productId,get_TrxName());
			MOrderLine orderLine = new MOrderLine(order);
			orderLine.setLine(seqNo);
			StringBuilder stringBuilder = new StringBuilder(name);
			if (!Util.isEmpty(description)) {
				stringBuilder.append(" - ").append(description);
			}
			orderLine.setDescription(stringBuilder.toString());
			//
			orderLine.setM_Product_ID(productId, true);
			setQuantityToOrder(orderLine, product, projectUomId, quantityEntered, quantityToOrder);
			orderLine.setPrice();
			orderLine.setC_Project_ID(fromProject.getC_Project_ID());
			if(projectPhaseId > 0) {
				orderLine.setC_ProjectPhase_ID(projectPhaseId);
			}
			if(projectTaskId > 0) {
				orderLine.setC_ProjectTask_ID(projectTaskId);
			}
 			BigDecimal price = priceActual;
			if (price!= null && price.compareTo(Env.ZERO) != 0)
				orderLine.setPrice(price);
			orderLine.setTax();
			orderLine.saveEx();
			return "@C_Order_ID@ " + order.getDocumentNo() + " (1)";
		}

		//	Project Phase Lines
		AtomicInteger count = new AtomicInteger(0);
		if(projectLines != null) {
		projectLines.stream()
				.forEach(projectLine -> {
					MOrderLine orderLine = new MOrderLine(order);
					orderLine.setLine(projectLine.getLine());
					orderLine.setDescription(projectLine.getDescription());
					//
					orderLine.setM_Product_ID(projectLine.getM_Product_ID(), true);
					MProduct product = new MProduct(getCtx(),projectLine.getM_Product_ID(),get_TrxName());
			        BigDecimal toOrder = projectLine.getPlannedQty().subtract(projectLine.getInvoicedQty());
			        setQuantityToOrder(orderLine, product, projectLine.get_ValueAsInt("C_UOM_ID"), (BigDecimal)projectLine.get_Value("Qtyentered"), toOrder);
					orderLine.setPrice();
					if (projectLine.getPlannedPrice() != null && projectLine.getPlannedPrice().compareTo(Env.ZERO) != 0)
						orderLine.setPrice(projectLine.getPlannedPrice());
					orderLine.setDiscount();
					orderLine.setTax();
					orderLine.setC_Project_ID(fromProject.getC_Project_ID());
					orderLine.setC_ProjectPhase_ID(projectLine.getC_ProjectPhase_ID());
					if(projectLine.get_Value("DatePromised") != null) {
						orderLine.setDatePromised((Timestamp) projectLine.get_Value("DatePromised"));
					}
					if(projectLine.getC_ProjectTask_ID() > 0) {
						orderLine.setC_ProjectTask_ID(projectLine.getC_ProjectTask_ID());
					}
					orderLine.saveEx();
					count.getAndUpdate(no -> no + 1);
				});    //	for all lines
		if (projectLines.size() != count.get())
			log.log(Level.SEVERE, "Lines difference - ProjectLines=" + projectLines.size() + " <> Saved=" + count.get());
		}
		//	Project Tasks
		if(tasks != null) {
			tasks.stream().filter(task -> task.getM_Product_ID() != 0).forEach(fromTask -> {
				{
					MOrderLine orderLine = new MOrderLine(order);
					orderLine.setLine(fromTask.getSeqNo());
					StringBuilder stringBuilder = new StringBuilder(fromTask.getName());
					if (fromTask.getDescription() != null && fromTask.getDescription().length() > 0)
						stringBuilder.append(" - ").append(fromTask.getDescription());
					orderLine.setDescription(stringBuilder.toString());
					orderLine.setM_Product_ID(fromTask.getM_Product_ID(), true);
					MProduct productLine = new MProduct(getCtx(),fromTask.getM_Product_ID(),get_TrxName());
					setQuantityToOrder(orderLine, productLine, fromTask.get_ValueAsInt("C_UOM_ID"), (BigDecimal)fromTask.get_Value("Qtyentered"), fromTask.getQty());
					orderLine.setPrice();
					orderLine.setC_Project_ID(fromProject.getC_Project_ID());
					orderLine.setC_ProjectPhase_ID(fromTask.getC_ProjectPhase_ID());
					orderLine.setC_ProjectTask_ID(fromTask.getC_ProjectTask_ID());
					orderLine.setTax();
					orderLine.saveEx();
					count.getAndUpdate(no -> no + 1);
				}
				});    //	for all lines
			if (tasks.size() != count.get() - projectLines.size())
				log.log(Level.SEVERE, "doIt - Lines difference - ProjectTasks=" + tasks.size() + " <> Saved=" + count.get());
		}
		return "@C_Order_ID@ " + order.getDocumentNo() + " (" + count + ")";
	}	//	doIt
	
	/**
	 * Set line Quantity
	 * @param orderLine
	 * @param product
	 * @param uomToId
	 * @param quantityEntered
	 * @param quantityOrdered
	 */
	private void setQuantityToOrder(MOrderLine orderLine, MProduct product, int uomToId, BigDecimal quantityEntered, BigDecimal quantityOrdered) {
		int uomId = product.getC_UOM_ID();
		if(uomToId > 0
				&& quantityEntered != null
				&& quantityEntered != Env.ZERO) {
			uomId = uomToId;
			if(uomId != product.getC_UOM_ID()) {
				BigDecimal convertedQuantity = MUOMConversion.convertProductFrom (getCtx(), product.getM_Product_ID(), uomToId, quantityEntered);
				if (convertedQuantity == null) {
					quantityEntered = quantityOrdered;
				} else {
					quantityOrdered = convertedQuantity;
				}
				orderLine.setQty(quantityEntered);
				orderLine.setQtyOrdered(quantityOrdered);
			} else { 
				orderLine.setQty(quantityOrdered);
			}
		} else { 
			orderLine.setQty(quantityOrdered);
		}
		orderLine.setC_UOM_ID(uomId);
	}

}	//	ProjectPhaseGenOrder
