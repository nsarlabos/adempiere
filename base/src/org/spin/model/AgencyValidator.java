/*************************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                              *
 * This program is free software; you can redistribute it and/or modify it    		 *
 * under the terms version 2 or later of the GNU General Public License as published *
 * by the Free Software Foundation. This program is distributed in the hope   		 *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied 		 *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           		 *
 * See the GNU General Public License for more details.                       		 *
 * You should have received a copy of the GNU General Public License along    		 *
 * with this program; if not, write to the Free Software Foundation, Inc.,    		 *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     		 *
 * For the text or an alternative of this public license, you may reach us    		 *
 * Copyright (C) 2012-2018 E.R.P. Consultores y Asociados, S.A. All Rights Reserved. *
 * Contributor(s): Yamel Senih www.erpya.com				  		                 *
 *************************************************************************************/
package org.spin.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.*;
import org.compiere.process.DocAction;
import org.compiere.process.OrderPOCreateAbstract;
import org.compiere.util.*;
import org.eevolution.service.dsl.ProcessBuilder;
import org.spin.process.CommissionOrderCreateAbstract;

import com.eevolution.model.MSContract;



/**
 * Model validator for agency particularities
 * @author Yamel Senih, ysenih@erpya.com , http://www.erpya.com
 */
public class AgencyValidator implements ModelValidator
{
	/**	Logger			*/
	private CLogger log = CLogger.getCLogger(getClass());
	/** Client			*/
	private int		clientId = -1;
	
	
	public void initialize (ModelValidationEngine engine, MClient client) {
		if (client != null) {	
			clientId = client.getAD_Client_ID();
		}
		engine.addModelChange(MProject.Table_Name, this);
		engine.addModelChange(MOrder.Table_Name, this);
		engine.addModelChange(MInvoice.Table_Name, this);
		engine.addModelChange(MOrderLine.Table_Name, this);
		engine.addModelChange(MCommissionLine.Table_Name, this);
		engine.addModelChange(MProjectTask.Table_Name, this);
		engine.addModelChange(MBPartner.Table_Name, this);
		engine.addModelChange(MRequest.Table_Name, this);
		engine.addModelChange(MRfQLineQty.Table_Name, this);
		engine.addModelChange(MAttachment.Table_Name, this);
		engine.addDocValidate(MOrder.Table_Name, this);
		engine.addDocValidate(I_S_TimeExpense.Table_Name, this);
		engine.addDocValidate(MTimeExpense.Table_Name, this);
		engine.addDocValidate(MSContract.Table_Name, this);
		engine.addDocValidate(MInvoice.Table_Name, this);
		engine.addDocValidate(MCommissionRun.Table_Name, this);
	}	//	initialize

	public String modelChange (PO po, int type) throws Exception {
		log.info(po.get_TableName() + " Type: "+type);
		if (type == TYPE_BEFORE_NEW || type == TYPE_BEFORE_CHANGE) {
			if(po instanceof MRequest) {
				MRequest request = (MRequest) po;
				if(request.getR_RequestType_ID() != 0) {
					MRequestType requestType = new MRequestType(request.getCtx(), request.getR_RequestType_ID(), request.get_TrxName());
					// Validates Approved on Request Type					
					if(requestType.get_ValueAsBoolean("IsApproved")) {
						// Validates Status be Completed
						MStatus status = new MStatus(request.getCtx(), request.get_ValueAsInt("R_Status_ID"), request.get_TrxName());
						if (status.isClosed() && !status.isFinalClose()) {
							// Validates Approved 1 on Request						
							if(request.get_ValueAsBoolean("IsApproved1")) {
								// Validates Approved 2 on Request
								if(!request.get_ValueAsBoolean("IsApproved2")) {
									throw new AdempiereException(Msg.getMsg(Env.getCtx(), "NotApproved2"));
								}
							}else {
								throw new AdempiereException(Msg.getMsg(Env.getCtx(), "NotApproved1"));
							}
						}
					}		
				}				
			} else if(po instanceof MRfQLineQty) {
				MRfQLineQty lineQuantity = (MRfQLineQty) po;
				MRfQLine line = MRfQLine.get(lineQuantity.getCtx(), lineQuantity.getC_RfQLine_ID(), lineQuantity.get_TrxName());
				if(line.get_ValueAsInt(I_C_Project.COLUMNNAME_C_Project_ID) > 0
						|| line.get_ValueAsInt(I_C_ProjectTask.COLUMNNAME_C_ProjectTask_ID) > 0
						|| line.get_ValueAsInt(I_C_ProjectPhase.COLUMNNAME_C_ProjectPhase_ID) > 0) {
					lineQuantity.setIsOfferQty(true);
					lineQuantity.setIsPurchaseQty(true);
				}

				if(lineQuantity.is_ValueChanged("BestResponseAmt")){

					String sql = "select coalesce(q.margin,0)" +
							" from c_rfqresponselineqty q" +
							" join c_rfqresponseline l on q.c_rfqresponseline_id = l.c_rfqresponseline_id" +
							" join c_rfqresponse h on l.c_rfqresponse_id = h.c_rfqresponse_id" +
							" where h.isselectedwinner = 'Y' and c_rfqlineqty_id = " + lineQuantity.get_ID();

					BigDecimal margin = DB.getSQLValueBDEx(lineQuantity.get_TrxName(), sql);

					if(margin.compareTo(Env.ZERO) > 0)
						lineQuantity.setMargin(margin);

				}

			} else if(po instanceof MAttachment) {
				ArrayList<String> messageList = new ArrayList<>();
				StringBuffer messageToSend = new StringBuffer();
				MAttachment attachment = (MAttachment) po;
				if(attachment.getAD_Table_ID() == I_R_Request.Table_ID) {
					MAttachmentEntry[] entries = attachment.getEntries();
					MRequest request = new MRequest(attachment.getCtx(), attachment.getRecord_ID(), attachment.get_TrxName());
					List<MAttachmentEntry> entryToAdd = new ArrayList<>();
					if(entries != null) {
						MAttachment requestAttachment = request.getAttachment(true);
						MAttachmentEntry[] requestEntries = null;
						if(requestAttachment != null
								&& requestAttachment.getAD_Attachment_ID() > 0) {
							requestEntries = requestAttachment.getEntries();
						}
						//	Add or match
						for(MAttachmentEntry entry : entries) {
							if(requestEntries == null) {
								entryToAdd.add(entry);
							} else {
								boolean existsEntry = false;
								for(MAttachmentEntry attachmentEntry : requestEntries) {
									if(attachmentEntry.getName().equals(entry.getName())) {
										existsEntry = true;
										break;
									}
								}
								if(!existsEntry) {
									entryToAdd.add(entry);
								}
							}
						}
						//	Add Request Update
						for(MAttachmentEntry entry : entryToAdd) {
							MRequestUpdate update = new MRequestUpdate(request);
							String message = "@File@ @Added@: ";
							if(entry.isGraphic()) {
								MImage image = new MImage(attachment.getCtx(), 0, attachment.get_TrxName());
								image.setBinaryData(entry.getData());
								image.setName(entry.getName());
								image.saveEx();
								update.set_ValueOfColumn(I_AD_Image.COLUMNNAME_AD_Image_ID, image.getAD_Image_ID());
								message = "@AD_Image_ID@ @Added@: ";
							}
							String translatedMessage = Msg.parseTranslation(attachment.getCtx(), message) + entry.getName();
							update.setResult(translatedMessage);
							//	
							update.saveEx();
							//	Add attachment to update
							if(!entry.isGraphic()) {
								MAttachment updateAttachment = update.createAttachment();
								updateAttachment.addEntry(entry);
								updateAttachment.addTextMsg(translatedMessage);
								updateAttachment.saveEx();
							}
							if(messageToSend.length() > 0) {
								messageToSend.append(Env.NL);
							}
							messageToSend.append(translatedMessage);
						}
						//	Notify
						if(messageToSend.length() > 0) {
							messageList.add(I_R_Request.COLUMNNAME_Result);
							request.setResult(messageToSend.toString());
							request.sendNotices(messageList, MMailText.EVENTTYPE_AutomaticTaskNewActivityNotice);
						}
					}
				}
			} else if(po instanceof MProject) {
				MProject project = (MProject) po;
				if(project.is_ValueChanged("M_PriceList_ID")){
					if(project.getM_PriceList_ID() > 0){
						MPriceList list = new MPriceList(project.getCtx(), project.getM_PriceList_ID(), project.get_TrxName());
						project.setC_Currency_ID(list.getC_Currency_ID());

						Timestamp today = TimeUtil.trunc(new Timestamp(System.currentTimeMillis()), TimeUtil.TRUNC_DAY);
						MPriceListVersion version = list.getPriceListVersion(today);

						if (version != null && version.get_ID() > 0){
							project.setM_PriceList_Version_ID(version.get_ID());
						} else project.setM_PriceList_Version_ID(0);
					}
				}
			}
		}
		if (type == TYPE_BEFORE_CHANGE) {
			if (po instanceof MProject) {
				MProject project = (MProject) po;
				if(project.get_ValueAsBoolean("IsApprovedAttachment")) {
					MAttachment projectAttachment = project.getAttachment(true);
					if (projectAttachment == null 
							|| projectAttachment.getAD_Attachment_ID() <= 0) {
						throw new AdempiereException(Msg.getMsg(Env.getCtx(), "AttachmentNotFound"));
					}
				}
			} else if(po instanceof MOrderLine) {
				if(po.is_ValueChanged(I_C_OrderLine.COLUMNNAME_Link_OrderLine_ID)) {
					MOrderLine orderLine = (MOrderLine) po;
					int projectPhaseId = orderLine.getC_ProjectPhase_ID();
					int projectTaskId = orderLine.getC_ProjectTask_ID();
					if(orderLine.getLink_OrderLine_ID() > 0) {
						MOrder order = orderLine.getParent();
						if(order.isSOTrx()) {
							MOrderLine generatedOrderLine = (MOrderLine) orderLine.getLink_OrderLine();
							MOrder generatedOrder = generatedOrderLine.getParent();
							if(!generatedOrder.isProcessed()) {
								MDocType sourceDocumentType = MDocType.get(order.getCtx(), order.getC_DocTypeTarget_ID());
								//Openup. Nicolas Sarlabos. #2752.
								if(sourceDocumentType.get_ValueAsBoolean("IsSetPOPriceFromSO")) {
									//si la linea de OV tiene definido PriceList entonces lo asigno, si no le asigno
									//si no le asigno el PriceEntered
									if(orderLine.getPriceList().compareTo(Env.ZERO) != 0){
										generatedOrderLine.setPriceEntered(orderLine.getPriceList());
										generatedOrderLine.setPriceActual(orderLine.getPriceList());
									} else if(orderLine.getPriceList().compareTo(Env.ZERO) == 0){
										generatedOrderLine.setPriceEntered(orderLine.getPriceEntered());
										generatedOrderLine.setPriceActual(orderLine.getPriceEntered());
									}
								}//Fin #2752.

								if(projectPhaseId > 0) {
									generatedOrderLine.setC_ProjectPhase_ID(projectPhaseId);
								} else if(projectTaskId > 0) {
									generatedOrderLine.setC_ProjectTask_ID(projectTaskId);
								}								
								if(orderLine.getC_Campaign_ID() != 0)
									generatedOrderLine.set_ValueOfColumn("C_Campaign_ID", orderLine.getC_Campaign_ID());
								if(orderLine.getUser1_ID() != 0)
									generatedOrderLine.set_ValueOfColumn("User1_ID", orderLine.getUser1_ID());
								if(orderLine.getC_Project_ID() != 0)
									generatedOrderLine.set_ValueOfColumn("C_Project_ID", orderLine.getC_Project_ID());
								if(orderLine.get_ValueAsInt("CUST_MediaType_ID") != 0)
									generatedOrderLine.set_ValueOfColumn("CUST_MediaType_ID", orderLine.get_ValueAsInt("CUST_MediaType_ID"));
								generatedOrderLine.saveEx();
							}
						}
					}
				}
			} else if(po instanceof MOrder) {
				if(po.is_ValueChanged(I_C_Order.COLUMNNAME_Link_Order_ID)) {
					MOrder order = (MOrder) po;
					if(order.getLink_Order_ID() > 0
							&& order.isSOTrx()) {
						MOrder linkSourceOrder = (MOrder) order.getLink_Order();
						linkSourceOrder.setDateOrdered(order.getDateOrdered());
						linkSourceOrder.setDatePromised(order.getDatePromised());
						if(order.isDropShip()) {
							linkSourceOrder.set_ValueOfColumn("IsDirectInvoice", order.get_ValueAsBoolean("IsDirectInvoice"));
						}
						if(order.get_ValueAsInt("C_ProjectPhase_ID") > 0) {
							linkSourceOrder.set_ValueOfColumn("C_ProjectPhase_ID", order.get_ValueAsInt("C_ProjectPhase_ID"));
						}
						linkSourceOrder.saveEx();
					}
				}
			} else if(po instanceof MProjectTask) {
				MProjectTask projectTask = (MProjectTask) po;
				if(projectTask.get_ValueAsBoolean("IsCustomerApproved")){
					if(projectTask.get_ValueAsBoolean("IsApprovedAttachment")) {
						MAttachment projectTaskAttachment = projectTask.getAttachment(true);
						if (projectTaskAttachment == null 
								|| projectTaskAttachment.getAD_Attachment_ID() <= 0) {
							throw new AdempiereException(Msg.getMsg(Env.getCtx(), "AttachmentNotFound"));
						}
					}
				}
			}
		} else if(type == TYPE_BEFORE_NEW) {
			if (po instanceof MCommissionLine) {
				MCommissionLine commissionLine = (MCommissionLine) po;
				MCommission commission = (MCommission) commissionLine.getC_Commission();
				if(commission != null
						&& commissionLine.get_ValueAsInt("Vendor_ID") <= 0) {
					if(commissionLine.get_ValueAsInt("C_Order_ID") > 0) {
						MOrder order = new MOrder(commission.getCtx(), commissionLine.get_ValueAsInt("C_Order_ID"), commission.get_TrxName());
						commissionLine.set_ValueOfColumn("Vendor_ID", order.getC_BPartner_ID());
					}
				}
			} else if(po instanceof MOrderLine) {
				MOrderLine orderLine = (MOrderLine) po;
				int projectPhaseId = orderLine.getC_ProjectPhase_ID();
				int projectTaskId = orderLine.getC_ProjectTask_ID();
				if(projectTaskId > 0) {
					MProjectTask projectTask = new MProjectTask(orderLine.getCtx(), projectTaskId,orderLine.get_TrxName());
					if(projectTask.getC_Campaign_ID() != 0)
						orderLine.set_ValueOfColumn("C_Campaign_ID", projectTask.getC_Campaign_ID());
					if(projectTask.getUser1_ID() != 0)
						orderLine.set_ValueOfColumn("User1_ID", projectTask.getUser1_ID());
					if(projectTask.get_ValueAsInt("CUST_MediaType_ID") != 0)
						orderLine.set_ValueOfColumn("CUST_MediaType_ID", projectTask.get_ValueAsInt("CUST_MediaType_ID"));
					MProjectPhase projectPhasefromTask = new MProjectPhase(orderLine.getCtx(), projectTask.getC_ProjectPhase_ID(),orderLine.get_TrxName());
					if(projectPhasefromTask.getC_Project_ID() != 0)
					orderLine.set_ValueOfColumn("C_Project_ID", projectPhasefromTask.getC_Project_ID());						
				} else if(projectPhaseId > 0) {
					MProjectPhase projectPhase = new MProjectPhase(orderLine.getCtx(), projectPhaseId, orderLine.get_TrxName());						
					if(projectPhase.getC_Campaign_ID() != 0)
						orderLine.set_ValueOfColumn("C_Campaign_ID", projectPhase.getC_Campaign_ID());
					if(projectPhase.getUser1_ID() != 0)
						orderLine.set_ValueOfColumn("User1_ID", projectPhase.getUser1_ID());
					if(projectPhase.getC_Project_ID() != 0)
						orderLine.set_ValueOfColumn("C_Project_ID", projectPhase.getC_Project_ID());
					if(projectPhase.get_ValueAsInt("CUST_MediaType_ID") != 0)
						orderLine.set_ValueOfColumn("CUST_MediaType_ID", projectPhase.get_ValueAsInt("CUST_MediaType_ID"));
				}
			} else if(po instanceof MOrder) {
				MOrder order = (MOrder) po;
				int orderprojectId = order.getC_Project_ID();
				if(orderprojectId > 0) {
					if(order.getC_ConversionType_ID() <= 0) order.setC_ConversionType_ID(MConversionType.TYPE_SPOT);
					MProject project = new MProject(order.getCtx(), orderprojectId,order.get_TrxName());
					// Validates Customer Approved
					if(!project.get_ValueAsBoolean("IsCustomerApproved")) {
						throw new AdempiereException(Msg.parseTranslation(Env.getCtx(), "@CustomerApprovedRequired@"));
					}
				}
				// Validates Order Has ProjectPorcentaje 
				int serviceContractId = order.get_ValueAsInt("S_Contract_ID");
				if(serviceContractId > 0) {
					MSContract serviceContract = new MSContract(order.getCtx(), serviceContractId, order.get_TrxName());
					//	Get first contract
					int projectId = new Query(order.getCtx(), I_C_Project.Table_Name, "S_Contract_ID = ?", po.get_TrxName())
						.setParameters(serviceContract.getS_Contract_ID())
						.setOnlyActiveRecords(true)
						.firstId();
					if(serviceContract.getUser1_ID() > 0) {
						order.setUser1_ID(serviceContract.getUser1_ID());
					}
					if(projectId > 0) {
						order.setC_Project_ID(projectId);
					}
				}

				if(order.getRef_Order_ID() > 0){

					MOrder refOrder = (MOrder) order.getRef_Order();

					if(refOrder.getUser3_ID() > 0){
						order.setUser1_ID(refOrder.getUser3_ID());
						order.setUser3_ID(refOrder.getUser1_ID());
					}
				}
			}
		} else if(type == TYPE_AFTER_CHANGE) {
			if (po instanceof MBPartner
					&& po.is_ValueChanged(I_C_BPartner.COLUMNNAME_BPartner_Parent_ID)) {
				MBPartner bPartner = (MBPartner) po;
				int treeId = MTree.getDefaultTreeIdFromTableId(bPartner.getAD_Client_ID(), I_C_BPartner.Table_ID);
				if(treeId > 0) {
					MTree tree = MTree.get(bPartner.getCtx(), treeId, null);
					MTree_NodeBP node = MTree_NodeBP.get(tree, bPartner.getC_BPartner_ID());
					if(node != null) {
						int parentId = bPartner.getBPartner_Parent_ID();
						if(parentId < 0) {
							parentId = 0;
						}
						node.setParent_ID(parentId);
						node.saveEx();
					}
				}
			}
		}

		//
		return null;
	}	//	modelChange
	
	@Override
	public String docValidate (PO po, int timing) {
		log.info(po.get_TableName() + " Timing: "+timing);
		//	Validate table
		if(po instanceof MOrder) {
			MOrder order = (MOrder) po;
			//	Validate
			MDocType  documentType = MDocType.get(order.getCtx(), order.getC_DocTypeTarget_ID());
			if(timing == TIMING_BEFORE_PREPARE) {
				if(order.get_ValueAsInt("S_Contract_ID") <= 0) {
					if(order.getC_Project_ID() > 0) {
						MProject parentProject = MProject.getById(order.getCtx(), order.getC_Project_ID(), order.get_TrxName());
						if(parentProject.get_ValueAsInt("S_Contract_ID") > 0) {
							order.set_ValueOfColumn("S_Contract_ID", parentProject.get_ValueAsInt("S_Contract_ID"));
							order.saveEx();
						}
					}
				}
				//	Validate User1_ID
				if(order.get_ValueAsInt("S_Contract_ID") > 0) {
					int user1Id = DB.getSQLValue(order.get_TrxName(), "SELECT p.User1_ID "
							+ "FROM S_ContractParties p "
							+ "WHERE S_Contract_ID = ? "
							+ "AND EXISTS(SELECT 1 FROM AD_User u WHERE u.AD_User_ID = p.AD_User_ID AND u.C_BPartner_ID = ?)", order.get_ValueAsInt("S_Contract_ID"), order.getC_BPartner_ID());
					//	
					if(user1Id > 0) {
						order.setUser1_ID(user1Id);
						order.saveEx();
					}
				}
				// Document type IsCustomerApproved = Y and order IsCustomerApproved = N
				if (documentType.get_ValueAsBoolean("IsApprovedRequired")) {
					if(!order.get_ValueAsBoolean("IsCustomerApproved")) {
						throw new AdempiereException(Msg.parseTranslation(Env.getCtx(), "@CustomerApprovedRequired@"));
					}
					MAttachment orderAttachment = order.getAttachment(true);
					if(orderAttachment == null
							|| orderAttachment.getAD_Attachment_ID() <= 0) {
						throw new AdempiereException(Msg.getMsg(Env.getCtx(), "AttachmentNotFound"));
					}
					//	Validate project reference
					if(order.getC_Project_ID() <= 0) {
						throw new AdempiereException(Msg.parseTranslation(Env.getCtx(), "@C_Project_ID@ @NotFound@"));
					}
					//	Document type IsCustomerApproved = Y and order IsCustomerApproved Y and order isAttachment() = N and project IsCustomerApproved = N
					MProject project = new MProject(order.getCtx(), order.getC_Project_ID(), null);
					if (!project.get_ValueAsBoolean("IsCustomerApproved")) {
						throw new AdempiereException(Msg.parseTranslation(Env.getCtx(), "@CustomerApprovedRequired@ @C_Project_ID@"));
					}
				}
				//	Validate Document Type for commission
				if(documentType.get_ValueAsInt("C_CommissionType_ID") > 0) {
					createCommissionForOrder(order, documentType.get_ValueAsInt("C_CommissionType_ID"), false);
				}
			} else if (timing == TIMING_AFTER_COMPLETE) {
				if(order.isSOTrx()) {
					//	For Sales Orders only
					if(order.isDropShip()) {
						//	For drop ship only
						ProcessBuilder.create(order.getCtx())
							.process(OrderPOCreateAbstract.getProcessId())
							.withParameter(OrderPOCreateAbstract.C_ORDER_ID, order.getC_Order_ID())
							.withParameter(OrderPOCreateAbstract.VENDOR_ID, order.getDropShip_BPartner_ID())
							.withParameter(MOrder.COLUMNNAME_DocAction, MOrder.DOCACTION_Complete)
							.withParameter("C_DocTypeDropShip_ID", documentType.get_ValueAsInt("C_DocTypeDropShip_ID"))
							.withoutTransactionClose()
							.execute(order.get_TrxName());
					}
				}
				//	Validate Document Type for commission
				if(documentType.get_ValueAsInt("C_CommissionType_ID") > 0) {
					createCommissionForOrder(order, documentType.get_ValueAsInt("C_CommissionType_ID"), true);
				}
				//	Generate Pre-Purchase reverse
				generateReverseAmount(order);
			} else if(timing == TIMING_AFTER_VOID) {
				//	For commissions
				new Query(order.getCtx(), I_C_CommissionRun.Table_Name, I_C_Order.COLUMNNAME_C_Order_ID + " = ? "
						+ "AND " + I_C_Invoice.COLUMNNAME_C_Invoice_ID + " IS NULL "
						+ "AND DocStatus = 'CO'", order.get_TrxName())
					.setOnlyActiveRecords(true)
					.setParameters(order.getC_Order_ID())
					.<MCommissionRun>list().forEach(commissionRun -> {
					if(!commissionRun.processIt(MCommissionRun.DOCACTION_Void)) {
						throw new AdempiereException(commissionRun.getProcessMsg());
					}
					commissionRun.saveEx();
				});
				//	For reverses
				new Query(order.getCtx(), I_C_Order.Table_Name, "ConsumptionOrder_ID = ? "
						+ "AND DocStatus = 'CO'", order.get_TrxName())
					.setOnlyActiveRecords(true)
					.setParameters(order.getC_Order_ID())
					.<MOrder>list().forEach(reverseOrder -> {
					if(!reverseOrder.processIt(MOrder.DOCACTION_Void)) {
						throw new AdempiereException(reverseOrder.getProcessMsg());
					}
					reverseOrder.saveEx();
				});
			} if(timing == TIMING_BEFORE_COMPLETE) {
//	
				if(order.isSOTrx()) {
					for(MOrderLine orderLine : order.getLines()) {
						if(orderLine.getM_Product_ID() != 0) {
							MPriceList  priceList = new MPriceList(order.getCtx(), order.getM_PriceList_ID(), order.get_TrxName());
							MPriceListVersion priceListVersion = new MPriceListVersion(priceList.getCtx(), priceList.getM_PriceList_ID(), priceList.get_TrxName());
							MProduct product = new MProduct (orderLine.getCtx(), orderLine.getM_Product_ID(), orderLine.get_TrxName());
							MProductPrice productPrice = new MProductPrice(orderLine.getCtx(), priceListVersion.getM_PriceList_Version_ID(), product.getM_Product_ID(), orderLine.get_TrxName());
							if (!productPrice.get_ValueAsBoolean("IsIncludedContract")) {
								BigDecimal priceEntered = orderLine.getPriceEntered();						
								if( priceEntered.compareTo(BigDecimal.ZERO) == 0) {
									throw new AdempiereException(Msg.parseTranslation(Env.getCtx(), "@PriceEntereddontBeZero@"));
								}
							}	
						}
					}
				} else {

					if (documentType.get_ValueAsBoolean("IsValidateSO")) {

						if(order.getLink_Order_ID() > 0){

							MOrder linkOrder = (MOrder) order.getLink_Order();

							if(!linkOrder.getDocStatus().equalsIgnoreCase(MOrder.STATUS_Completed))
								throw new AdempiereException(Msg.parseTranslation(Env.getCtx(), "@Link_Order_ID@" + ": " + "@CreateShipment.OrderNotCompleted@"));

						} else throw new AdempiereException(Msg.parseTranslation(Env.getCtx(), "@Link_Order_ID@" + ": " + "@NotFound@"));
					}
				}//
			} 
		} else if(po instanceof MCommissionRun) {
			MCommissionRun commissionRun = (MCommissionRun) po;
			if(timing == TIMING_AFTER_VOID) {
				new Query(commissionRun.getCtx(), I_C_Order.Table_Name, I_C_CommissionRun.COLUMNNAME_C_CommissionRun_ID + " = ? "
						+ "AND DocStatus = 'CO'", commissionRun.get_TrxName())
					.setOnlyActiveRecords(true)
					.setParameters(commissionRun.getC_CommissionRun_ID())
					.<MOrder>list().forEach(order -> {
					if(!order.processIt(MOrder.DOCACTION_Void)) {
						throw new AdempiereException(order.getProcessMsg());
					}
					order.saveEx();
				});
			}
		} else if(po instanceof MTimeExpense) {
			MTimeExpense expenseReport = (MTimeExpense) po;
			if(timing == TIMING_BEFORE_COMPLETE) {
//				for(MTimeExpenseLine expenseReportLine : expenseReport.getLines()) {
//					if(expenseReportLine.getC_Project_ID() <= 0) {
//						continue;
//					}
//					StringBuffer whereClause = new StringBuffer();
//					whereClause.append("C_Project_ID = ? "
//							+ "AND EXISTS(SELECT 1 FROM S_TimeExpense te ")
//						.append("WHERE S_TimeExpenseLine.S_TimeExpense_ID = te.S_TimeExpense_ID AND (te.DocStatus = ? OR te.S_TimeExpense_ID = ?))");
//					BigDecimal expenseAmt = new Query(po.getCtx(), I_S_TimeExpenseLine.Table_Name, whereClause.toString(), po.get_TrxName())
//							.setClient_ID()
//							.setParameters(expenseReportLine.getC_Project_ID(), MTimeExpense.DOCSTATUS_Completed, expenseReportLine.getS_TimeExpense_ID())
//							.sum(MTimeExpenseLine.COLUMNNAME_ExpenseAmt);
//					//	
//					MProject project = MProject.getById(expenseReport.getCtx(), expenseReportLine.getC_Project_ID(), expenseReport.get_TrxName());
//					BigDecimal plannedAmt = project.getPlannedAmt();
//					if(plannedAmt != null
//							&& plannedAmt.compareTo(expenseAmt) < 0) {
//						DecimalFormat format = DisplayType.getNumberFormat(DisplayType.Amount);
//						throw new AdempiereException(Msg.parseTranslation(Env.getCtx(), 
//								"@S_TimeExpenseLine_ID@ > @PlannedAmt@ @C_Project_ID@ [" + project.getName() + ", " + format.format(plannedAmt) + "] @ExpenseAmt@ " + format.format(expenseAmt)));
//					}
//				}
			} else if(timing == TIMING_AFTER_COMPLETE) {
				Hashtable<Integer, Hashtable<Integer, BigDecimal>> orders = new Hashtable<Integer, Hashtable<Integer, BigDecimal>>();
				for(MTimeExpenseLine line : expenseReport.getLines()) {
					//	Validate Orders
					int salesOrderLineId = line.getC_OrderLine_ID();
					int linkOrderLineId = line.get_ValueAsInt("Link_OrderLine_ID");
					if(salesOrderLineId <= 0
							&& linkOrderLineId <= 0
							|| line.getQty() == null
							|| line.getQty().compareTo(Env.ZERO) <= 0) {
						continue;
					}
					//	For sales
					if(salesOrderLineId > 0) {
						MOrderLine salesOrderLine = new MOrderLine(expenseReport.getCtx(), salesOrderLineId, expenseReport.get_TrxName());
						Hashtable<Integer, BigDecimal> salesOrderLines = orders.get(salesOrderLine.getC_Order_ID());
						if(salesOrderLines == null) {
							salesOrderLines = new Hashtable<Integer, BigDecimal>();
						}
						//	Add
						salesOrderLines.put(salesOrderLine.getC_OrderLine_ID(), line.getQty());
						orders.put(salesOrderLine.getC_Order_ID(), salesOrderLines);
					}
					//	For purchases
					if(linkOrderLineId > 0) {
						MOrderLine salesOrderLine = new MOrderLine(expenseReport.getCtx(), linkOrderLineId, expenseReport.get_TrxName());
						Hashtable<Integer, BigDecimal> purchaseOrderLines = orders.get(salesOrderLine.getC_Order_ID());
						if(purchaseOrderLines == null) {
							purchaseOrderLines = new Hashtable<Integer, BigDecimal>();
						}
						//	Add
						purchaseOrderLines.put(salesOrderLine.getC_OrderLine_ID(), line.getQty());
						orders.put(salesOrderLine.getC_Order_ID(), purchaseOrderLines);
					}
				}
				//	Generate from orders
				orders.entrySet().stream().forEach(orderSet -> {
					generateInOutFromOrder(expenseReport, orderSet.getKey(), orderSet.getValue());
				});
			}
		} else if(po instanceof MInvoice) {
			MInvoice invoice = (MInvoice) po;
			if(timing == TIMING_BEFORE_PREPARE) {
				if(invoice.get_ValueAsInt("S_Contract_ID") <= 0) {
					if(invoice.getC_Project_ID() > 0) {
						MProject parentProject = MProject.getById(invoice.getCtx(), invoice.getC_Project_ID(), invoice.get_TrxName());
						if(parentProject.get_ValueAsInt("S_Contract_ID") > 0) {
							invoice.set_ValueOfColumn("S_Contract_ID", parentProject.get_ValueAsInt("S_Contract_ID"));
							invoice.saveEx();
						}
					}
				}
				//	Validate IsAllowToInvoice
				if(invoice.getC_Order_ID() > 0) {
					MOrder order = (MOrder) invoice.getC_Order();
					//	Set Project Phase and Task
					if(order.get_ValueAsInt(I_C_ProjectPhase.COLUMNNAME_C_ProjectPhase_ID) > 0) {
						invoice.set_ValueOfColumn(I_C_ProjectPhase.COLUMNNAME_C_ProjectPhase_ID, order.get_ValueAsInt(I_C_ProjectPhase.COLUMNNAME_C_ProjectPhase_ID));
					}
					if(order.get_ValueAsInt(I_C_ProjectTask.COLUMNNAME_C_ProjectTask_ID) > 0) {
						invoice.set_ValueOfColumn(I_C_ProjectTask.COLUMNNAME_C_ProjectTask_ID, order.get_ValueAsInt(I_C_ProjectTask.COLUMNNAME_C_ProjectTask_ID));
					}
					invoice.saveEx();
					if(invoice.isSOTrx()) {
						if(!order.get_ValueAsBoolean("IsAllowToInvoice")) {
							throw new AdempiereException("@C_Order_ID@ " + order.getDocumentNo() + " @IsAllowToInvoiceRequired@");
						}
					}
				}
			} else if(timing == TIMING_AFTER_COMPLETE) {
				if(!invoice.isReversal()) {
					//	Validate
					MDocType  documentType = MDocType.get(invoice.getCtx(), invoice.getC_DocTypeTarget_ID());
					//	Validate Document Type for commission
					if(documentType.get_ValueAsInt("C_CommissionType_ID") > 0) {
						createCommissionForInvoice(invoice, documentType.get_ValueAsInt("C_CommissionType_ID"), true);
					}
				}
			} else if(timing == TIMING_AFTER_REVERSECORRECT
					|| timing == TIMING_AFTER_REVERSEACCRUAL
					|| timing == TIMING_AFTER_VOID) {
				new Query(invoice.getCtx(), I_C_CommissionRun.Table_Name, I_C_Invoice.COLUMNNAME_C_Invoice_ID + " = ? "
						+ "AND DocStatus = 'CO'", invoice.get_TrxName())
					.setOnlyActiveRecords(true)
					.setParameters(invoice.getC_Invoice_ID())
					.<MCommissionRun>list().forEach(commissionRun -> {
					if(!commissionRun.processIt(MCommissionRun.DOCACTION_Void)) {
						throw new AdempiereException(commissionRun.getProcessMsg());
					}
				});
			}
		} else if(po instanceof MSContract) {
			if(timing == TIMING_BEFORE_COMPLETE) {
				MSContract serviceContract = (MSContract) po;
				String whereClause = "S_Contract_ID = ?";
				X_C_CommissionSalesRep salesRep = new Query(po.getCtx(), I_C_CommissionSalesRep.Table_Name, whereClause, po.get_TrxName())
					.setParameters(serviceContract.getS_Contract_ID())
					.setOnlyActiveRecords(true)
					.<X_C_CommissionSalesRep>first();
				if(salesRep != null
						&& salesRep.getC_CommissionSalesRep_ID() > 0) {
					BigDecimal sumPercent = new Query(po.getCtx(), I_C_CommissionSalesRep.Table_Name, whereClause, po.get_TrxName())
							.setParameters(serviceContract.getS_Contract_ID())
							.sum("AmtMultiplier");
					if(sumPercent.compareTo(Env.ONEHUNDRED) != 0){					
						throw new AdempiereException(Msg.getMsg(Env.getCtx(), "TotalPercentageIsNot100"));
					}
				} else { 
//					List<MCommissionLine> c = new Query(po.getCtx(), I_C_CommissionLine.Table_Name, whereClause, po.get_TrxName())
//							.setParameters(serviceContract.getS_Contract_ID())
//							.setOnlyActiveRecords(true)
//							.<MCommissionLine>list();
//					//	Iterate
//					
				}
			}
		}
		return null;
	}	//	docValidate
	
	/**
	 * Generate In/Out from Sales or Purchase Orders
	 * @param orderId
	 * @param lines
	 */
	private void generateInOutFromOrder(MTimeExpense expenseReport, int orderId, Hashtable<Integer, BigDecimal> lines) {
		MOrder order = new MOrder(expenseReport.getCtx(), orderId, expenseReport.get_TrxName());
		MInOut inOut = new MInOut(order, 0, expenseReport.getDateReport());
		inOut.setM_Warehouse_ID(order.getM_Warehouse_ID());
		inOut.saveEx();
		AtomicReference<BigDecimal> totalOrdered = new AtomicReference<BigDecimal>(Env.ZERO);
		AtomicReference<BigDecimal> totalDelivered = new AtomicReference<BigDecimal>(Env.ZERO);
		lines.entrySet().stream().forEach(linesSet -> {
			MOrderLine orderLine = new MOrderLine(expenseReport.getCtx(), linesSet.getKey(), expenseReport.get_TrxName());
			BigDecimal toDeliver = linesSet.getValue();
			MInOutLine inOutLine = new MInOutLine (inOut);
			inOutLine.setOrderLine(orderLine, 0, toDeliver);
			inOutLine.setQty(toDeliver);
		    inOutLine.saveEx();
		    totalOrdered.updateAndGet(amount -> amount.add(orderLine.getLineNetAmt()));
		    totalDelivered.updateAndGet(amount -> amount.add(orderLine.getPriceActual().multiply(toDeliver)));
		});
		//	Complete In/Out
		inOut.setDocStatus(MInOut.DOCSTATUS_Drafted);
		inOut.processIt(MInOut.ACTION_Complete);
		inOut.saveEx();
		//	Generate Delivery for Commission
		if(totalOrdered.get() != null
				&& totalOrdered.get().compareTo(Env.ZERO) > 0
				&& totalDelivered.get() != null
				&& totalDelivered.get().compareTo(Env.ZERO) > 0) {
			//	Calculate Percentage
			BigDecimal multiplier = Env.ONE.divide(totalOrdered.get(), MathContext.DECIMAL128).multiply(totalDelivered.get());
			generateInOutFromCommissionOrder(order, multiplier);
		}
	}
	
	/**
	 * Reverse amount of pre-purchase order from a purchase order
	 * @param sourceOrder
	 */
	private void generateReverseAmount(MOrder sourceOrder) {
		int projectPhaseId = sourceOrder.get_ValueAsInt("C_ProjectPhase_ID");
		if(projectPhaseId <= 0) {
			return;
		}
		//	
		MDocType documentType = MDocType.get(sourceOrder.getCtx(), sourceOrder.getC_DocTypeTarget_ID());
		if(documentType.get_ValueAsBoolean("IsConsumePreOrder")) {
			int reverseDocumentTypeId = documentType.get_ValueAsInt("C_DocTypeReversal_ID");
			//	find all purchase order of pre-purchase
			MOrder preOrder = new Query(sourceOrder.getCtx(), I_C_Order.Table_Name, "DocStatus = 'CO' "
					+ "AND C_ProjectPhase_ID = ? "
					+ "AND IsSOTrx = '" + (sourceOrder.isSOTrx()? "Y": "N") + "' "
					+ "AND EXISTS(SELECT 1 FROM C_DocType dt WHERE dt.C_DocType_ID = C_Order.C_DocType_ID AND dt.IsPreOrder = 'Y')", sourceOrder.get_TrxName())
				.setParameters(projectPhaseId)
				.first();
			//	Validate
			if(preOrder != null
					&& preOrder.getC_Order_ID() > 0) {
				BigDecimal consumeAmount = DB.getSQLValueBD(sourceOrder.get_TrxName(), "SELECT SUM(GrandTotal) "
						+ "FROM C_Order o "
						+ "WHERE o.DocStatus IN('CO') "
						+ "AND o.PreOrder_ID = ? "
						+ "AND o.IsSOTrx = '" + (sourceOrder.isSOTrx()? "Y": "N") + "' "
						+ "AND EXISTS(SELECT 1 FROM C_DocType dt WHERE dt.C_DocType_ID = o.C_DocType_ID AND dt.IsConsumePreOrder = 'Y')", preOrder.getC_Order_ID());
				//	Validate
				if(consumeAmount == null) {
					consumeAmount = Env.ZERO;
				}
				consumeAmount = consumeAmount.add(sourceOrder.getGrandTotal());
				if(consumeAmount.compareTo(preOrder.getGrandTotal()) > 0) {
					DecimalFormat format = DisplayType.getNumberFormat(DisplayType.Amount);
					throw new AdempiereException("[@ConsumedAmt@] > @PreOrderAmt@ (@PreOrderAmt@ = " 
							+ format.format(preOrder.getGrandTotal()) + ", @ConsumedAmt@ = " + format.format(consumeAmount) 
							+ Env.NL + "@amount.difference@ = " + format.format(preOrder.getGrandTotal().subtract(consumeAmount)) + ")");
				}
				//	Generate document
				if(reverseDocumentTypeId > 0) {
					MOrder reverseOrder = new MOrder(sourceOrder.getCtx(), 0, sourceOrder.get_TrxName());
					PO.copyValues(preOrder, reverseOrder);
					reverseOrder.setDocumentNo(null);
					reverseOrder.setC_DocTypeTarget_ID(reverseDocumentTypeId);
					reverseOrder.setDateOrdered(sourceOrder.getDateOrdered());
					reverseOrder.setDatePromised(sourceOrder.getDatePromised());
					reverseOrder.setPOReference(sourceOrder.getDocumentNo());
					reverseOrder.addDescription(Msg.parseTranslation(sourceOrder.getCtx(), "@Generated@ [@C_Order_ID@ " + sourceOrder.getDocumentNo()) + "]");
					reverseOrder.setDocStatus(MOrder.DOCSTATUS_Drafted);
					reverseOrder.setDocAction(MOrder.DOCACTION_Complete);
					reverseOrder.setTotalLines(Env.ZERO);
					reverseOrder.setGrandTotal(Env.ZERO);
					reverseOrder.setIsSOTrx(sourceOrder.isSOTrx());
					reverseOrder.setRef_Order_ID(-1);
					reverseOrder.setIsDropShip(false);
					reverseOrder.setDropShip_BPartner_ID(0);
					reverseOrder.setDropShip_Location_ID(0);
					reverseOrder.setDropShip_User_ID(0);
					reverseOrder.set_ValueOfColumn("ConsumptionOrder_ID", sourceOrder.getC_Order_ID());
					reverseOrder.set_ValueOfColumn("PreOrder_ID", preOrder.getC_Order_ID());
					reverseOrder.saveEx();
					//	Add Line
					MOrderLine preOrderLine = preOrder.getLines(true, null)[0];
					
					MOrderLine reverseOrderLine = new MOrderLine(reverseOrder);
					PO.copyValues(reverseOrderLine, preOrderLine);
					reverseOrderLine.setOrder(reverseOrder);
					reverseOrderLine.setProduct(preOrderLine.getProduct());
					reverseOrderLine.setLineNetAmt(Env.ZERO);
					reverseOrderLine.setQty(Env.ONE);
					reverseOrderLine.setPrice(sourceOrder.getTotalLines().negate());
					reverseOrderLine.setTax();
					reverseOrderLine.saveEx();
					//	Complete
					if(!reverseOrder.processIt(MOrder.DOCACTION_Complete)) {
						throw new AdempiereException(reverseOrder.getProcessMsg());
					}
					reverseOrder.saveEx();
					//	Set pre Order
					if(sourceOrder.get_ValueAsInt("PreOrder_ID") <= 0) {
						sourceOrder.set_ValueOfColumn("PreOrder_ID", preOrder.getC_Order_ID());
					}
				}
			}
		}
	}
	
	/**
	 * Generate Delivery from commission Order that are generated from order
	 * @param order
	 * @param multiplier
	 */
	private void generateInOutFromCommissionOrder(MOrder order, BigDecimal multiplier) {
		new Query(order.getCtx(), I_C_Order.Table_Name, 
				"DocStatus = 'CO' "
				+ "AND EXISTS(SELECT 1 FROM C_CommissionRun cr "
				+ "WHERE cr.C_CommissionRun_ID = C_Order.C_CommissionRun_ID "
				+ "AND cr.C_Order_ID = ?) "
				+ "AND EXISTS(SELECT 1 FROM C_OrderLine ol "
				+ "WHERE ol.C_Order_ID = C_Order.C_Order_ID "
				+ "AND ol.QtyOrdered > COALESCE(QtyDelivered, 0))", order.get_TrxName())
			.setOnlyActiveRecords(true)
			.setParameters(order.getC_Order_ID())
			.<MOrder>list()
			.stream().forEach(commissionOrder -> {
				for(MOrderLine line : commissionOrder.getLines()) {
					MInOut shipment = new MInOut(commissionOrder, 0, commissionOrder.getDateOrdered());
					shipment.setM_Warehouse_ID(line.getM_Warehouse_ID());
					shipment.setMovementDate(line.getDatePromised());
					shipment.saveEx();
					//	Add Line
					BigDecimal qtyToDeliver = line.getQtyOrdered().multiply(multiplier);
					MInOutLine sline = new MInOutLine(shipment);
					sline.setOrderLine(line, 0, qtyToDeliver);
					sline.setQtyEntered(qtyToDeliver);
					sline.setC_UOM_ID(line.getC_UOM_ID());
					sline.setQty(qtyToDeliver);
					sline.setM_Warehouse_ID(line.getM_Warehouse_ID());
					sline.saveEx();
					//	Process It
					if (!shipment.processIt(DocAction.ACTION_Complete)) {
						log.warning("Failed: " + shipment);
					}
					shipment.saveEx();
//					ProcessBuilder.create(order.getCtx())
//						.process(OrderLineCreateShipmentAbstract.getProcessId())
//						.withRecordId(I_C_OrderLine.Table_ID, line.getC_OrderLine_ID())
//						.withParameter(OrderLineCreateShipmentAbstract.DOCACTION, DocAction.ACTION_Complete)
//						.withoutTransactionClose()
//					.execute(order.get_TrxName());
				}
			});
	}
	
	/**
	 * Create a commission based on rules defined and get result inside order line 
	 * it is only running if exists a flag for document type named (Calculate commission for Order)
	 * @param order
	 * @param
	 */
	private void createCommissionForOrder(MOrder order, int commissionTypeId, boolean splitDocuments) {
		removeLineFromCommission(order, commissionTypeId);
		new Query(order.getCtx(), I_C_Commission.Table_Name, I_C_CommissionType.COLUMNNAME_C_CommissionType_ID + " = ? "
				+ "AND IsSplitDocuments = ?", order.get_TrxName())
			.setOnlyActiveRecords(true)
			.setParameters(commissionTypeId, (splitDocuments? "Y": "N"))
			.<MCommission>list().forEach(commissionDefinition -> {
				int documentTypeId = MDocType.getDocType(MDocType.DOCBASETYPE_SalesCommission, order.getAD_Org_ID());
				MCommissionRun commissionRun = new MCommissionRun(commissionDefinition);
				commissionRun.setDateDoc(order.getDateOrdered());
				commissionRun.setC_DocType_ID(documentTypeId);
				commissionRun.setDescription(Msg.parseTranslation(order.getCtx(), "@Generate@: @C_Order_ID@ - " + order.getDocumentNo()));
				commissionRun.set_ValueOfColumn("C_Order_ID", order.getC_Order_ID());
				commissionRun.setAD_Org_ID(order.getAD_Org_ID());
				commissionRun.saveEx();
				//	Process commission
				commissionRun.addFilterValues("C_Order_ID", order.getC_Order_ID());
				commissionRun.setDocStatus(MCommissionRun.DOCSTATUS_Drafted);
				//	Complete
				if(commissionRun.processIt(MCommissionRun.DOCACTION_Complete)) {
					commissionRun.updateFromAmt();
					commissionRun.saveEx();
					if(commissionRun.getGrandTotal() != null
							&& commissionRun.getGrandTotal().compareTo(Env.ZERO) > 0) {
						if(commissionDefinition.get_ValueAsBoolean("IsSplitDocuments")) {
							ProcessBuilder.create(order.getCtx())
								.process(CommissionOrderCreateAbstract.getProcessId())
								.withRecordId(I_C_CommissionRun.Table_ID, commissionRun.getC_CommissionRun_ID())
								.withParameter(CommissionOrderCreateAbstract.ISSOTRX, true)
								.withParameter(CommissionOrderCreateAbstract.DATEORDERED, order.getDateOrdered())
								.withParameter(CommissionOrderCreateAbstract.DOCACTION, DocAction.ACTION_Complete)
								.withParameter(CommissionOrderCreateAbstract.C_BPARTNER_ID, order.getC_BPartner_ID())
								.withParameter(CommissionOrderCreateAbstract.C_DOCTYPE_ID, commissionDefinition.get_ValueAsInt("C_DocTypeOrder_ID"))
								.withoutTransactionClose()
							.execute(order.get_TrxName());
						} else {
							commissionRun.getCommissionAmtList().stream()
							.filter(commissionAmt -> commissionAmt.getCommissionAmt() != null 
								&& commissionAmt.getCommissionAmt().compareTo(Env.ZERO) > 0).forEach(commissionAmt -> {
									MOrderLine orderLine = new MOrderLine(order);
									orderLine.setC_Charge_ID(commissionDefinition.getC_Charge_ID());
									orderLine.setQty(Env.ONE);
									orderLine.setPrice(commissionAmt.getCommissionAmt());
									orderLine.setTax();
									orderLine.saveEx(order.get_TrxName());
							});
						}
					}
				} else {
					throw new AdempiereException(commissionRun.getProcessMsg());
				}
			});
	}
	
	/**
	 * Create a commission based on rules defined and get result inside order line 
	 * it is only running if exists a flag for document type named (Calculate commission for Order)
	 * @param invoice
	 * @param
	 */
	private void createCommissionForInvoice(MInvoice invoice, int commissionTypeId, boolean splitDocuments) {
		removeLineFromCommission(invoice, commissionTypeId);
		new Query(invoice.getCtx(), I_C_Commission.Table_Name, I_C_CommissionType.COLUMNNAME_C_CommissionType_ID + " = ? "
				+ "AND IsSplitDocuments = ?", invoice.get_TrxName())
			.setOnlyActiveRecords(true)
			.setParameters(commissionTypeId, (splitDocuments? "Y": "N"))
			.<MCommission>list().forEach(commissionDefinition -> {
				int documentTypeId = MDocType.getDocType(MDocType.DOCBASETYPE_SalesCommission, invoice.getAD_Org_ID());
				MCommissionRun commissionRun = new MCommissionRun(commissionDefinition);
				commissionRun.setDateDoc(invoice.getDateInvoiced());
				commissionRun.setC_DocType_ID(documentTypeId);
				commissionRun.setDescription(Msg.parseTranslation(invoice.getCtx(), "@Generate@: @C_Order_ID@ - " + invoice.getDocumentNo()));
				if(invoice.getC_Order_ID() > 0) {
					commissionRun.set_ValueOfColumn("C_Order_ID", invoice.getC_Order_ID());
				}
				commissionRun.set_ValueOfColumn("C_Invoice_ID", invoice.getC_Invoice_ID());
				commissionRun.setAD_Org_ID(invoice.getAD_Org_ID());
				commissionRun.saveEx();
				//	Process commission
				commissionRun.addFilterValues("C_Invoice_ID", invoice.getC_Invoice_ID());
				commissionRun.setDocStatus(MCommissionRun.DOCSTATUS_Drafted);
				//	Complete
				if(commissionRun.processIt(MCommissionRun.DOCACTION_Complete)) {
					commissionRun.updateFromAmt();
					commissionRun.saveEx();
					if(commissionRun.getGrandTotal() != null
							&& commissionRun.getGrandTotal().compareTo(Env.ZERO) > 0) {
						if(commissionDefinition.get_ValueAsBoolean("IsSplitDocuments")) {
							ProcessBuilder.create(invoice.getCtx())
								.process(CommissionOrderCreateAbstract.getProcessId())
								.withRecordId(I_C_CommissionRun.Table_ID, commissionRun.getC_CommissionRun_ID())
								.withParameter(CommissionOrderCreateAbstract.ISSOTRX, true)
								.withParameter(CommissionOrderCreateAbstract.DATEORDERED, invoice.getDateInvoiced())
								.withParameter(CommissionOrderCreateAbstract.DOCACTION, DocAction.ACTION_Complete)
								.withParameter(CommissionOrderCreateAbstract.C_BPARTNER_ID, invoice.getC_BPartner_ID())
								.withParameter(CommissionOrderCreateAbstract.C_DOCTYPE_ID, commissionDefinition.get_ValueAsInt("C_DocTypeOrder_ID"))
								.withoutTransactionClose()
							.execute(invoice.get_TrxName());
						} else {
							commissionRun.getCommissionAmtList().stream()
							.filter(commissionAmt -> commissionAmt.getCommissionAmt() != null 
								&& commissionAmt.getCommissionAmt().compareTo(Env.ZERO) > 0).forEach(commissionAmt -> {
									MInvoiceLine invoiceLine = new MInvoiceLine(invoice);
									invoiceLine.setC_Charge_ID(commissionDefinition.getC_Charge_ID());
									invoiceLine.setQty(Env.ONE);
									invoiceLine.setPrice(commissionAmt.getCommissionAmt());
									invoiceLine.setTax();
									invoiceLine.saveEx(invoice.get_TrxName());
							});
						}
					}
				} else {
					throw new AdempiereException(commissionRun.getProcessMsg());
				}
			});
	}
	
	/**
	 * Remove Line From Commission
	 * @param order
	 */
	private void removeLineFromCommission(MOrder order, int commissionTypeId) {
		String whereClause = " AND EXISTS(SELECT 1 FROM C_Commission c WHERE c.C_CommissionType_ID = " + commissionTypeId 
				+ " AND c.C_Charge_ID = C_OrderLine.C_Charge_ID)";
		for(MOrderLine line : order.getLines(whereClause, "")) {
			line.deleteEx(true);
		}
	}
	
	/**
	 * Remove Line From Commission
	 * @param invoice
	 */
	private void removeLineFromCommission(MInvoice invoice, int commissionTypeId) {
		String whereClause = "EXISTS(SELECT 1 FROM C_Commission c WHERE c.C_CommissionType_ID = " + commissionTypeId 
				+ " AND c.C_Charge_ID = C_InvoiceLine.C_Charge_ID)";
		List<MInvoiceLine> invoiceLineList = new Query(invoice.getCtx(), MInvoiceLine.Table_Name, whereClause, invoice.get_TrxName())
			.<MInvoiceLine>list();
		for(MInvoiceLine line : invoiceLineList) {
			line.deleteEx(true);
		}
	}
        
	
	@Override
	public int getAD_Client_ID() {
		return clientId;
	}

	@Override
	public String login(int AD_Org_ID, int AD_Role_ID, int AD_User_ID) {
		return null;
	}
}	//	AgencyValidator