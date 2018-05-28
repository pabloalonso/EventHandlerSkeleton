package com.bonitasoft.ps.event.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.bonitasoft.ps.util.Notifier;
import org.bonitasoft.engine.bpm.flownode.ActivityStates;
import org.bonitasoft.engine.core.process.instance.model.SActivityInstance;
import org.bonitasoft.engine.data.instance.api.DataInstanceService;
import org.bonitasoft.engine.data.instance.exception.SDataInstanceException;
import org.bonitasoft.engine.data.instance.model.SDataInstance;
import org.bonitasoft.engine.events.model.SEvent;
import org.bonitasoft.engine.events.model.SHandler;
import org.bonitasoft.engine.events.model.SHandlerExecutionException;
import org.bonitasoft.engine.log.technical.TechnicalLogSeverity;
import org.bonitasoft.engine.log.technical.TechnicalLoggerService;
import org.bonitasoft.engine.service.TenantServiceAccessor;
import org.bonitasoft.engine.service.impl.ServiceAccessorFactory;

/**
 * @author Pablo Alonso de Linaje García
 */
public class TaskCompleteEventHandler implements SHandler<SEvent> {
    private static final TechnicalLogSeverity ERROR_SEVERITY = TechnicalLogSeverity.valueOf("ERROR");
    private static final String TASK = "task";
    private static final String DATA_CONTAINER = "PROCESS_INSTANCE";
    private final TechnicalLoggerService technicalLoggerService;
    private final Long tenantId;
    private TechnicalLogSeverity technicalLogSeverity;
    private final String variableName;
    private static final String ACTIVITYINSTANCE_CLOSED = "ACTIVITYINSTANCE_STATE_UPDATED";


    public TaskCompleteEventHandler (TechnicalLoggerService technicalLoggerService, String loggerSeverity, String variableName, long tenantId) {
        this.technicalLoggerService = technicalLoggerService;

        //set desired logging level
        this.technicalLogSeverity = TechnicalLogSeverity.valueOf(loggerSeverity);

        //variable to check in the process
        this.variableName = variableName;

        this.tenantId = tenantId;
    }

    @Override
    public void execute(SEvent sEvent) throws SHandlerExecutionException {
        try {
            if (technicalLoggerService.isLoggable(this.getClass(), technicalLogSeverity)) {
                technicalLoggerService.log(this.getClass(), technicalLogSeverity, "TaskCompleteEventHandler: executing event " + sEvent.getType());
            }

            Map<String, Object> data = new HashMap<>();
            Object eventObject = sEvent.getObject();

            if (eventObject instanceof SActivityInstance) {
                SActivityInstance activityInstance = (SActivityInstance) eventObject;
                data.put(variableName, getProcessData(activityInstance.getRootProcessInstanceId(), variableName));
                data.put(TASK, getTaskInfo(activityInstance));
                if (technicalLoggerService.isLoggable(this.getClass(), technicalLogSeverity)) {
                    technicalLoggerService.log(this.getClass(), technicalLogSeverity, "TaskCompleteEventHandler: Notify: " + data.toString());
                }
                Notifier.notify(data);

            }
        }catch (Exception e){
            // In case of exception we write in the log
            if (technicalLoggerService.isLoggable(this.getClass(), technicalLogSeverity)) {
                technicalLoggerService.log(this.getClass(),ERROR_SEVERITY , "TaskCompleteEventHandler: ERROR executing event " + sEvent, e);

            }
        }
    }

    private Object getTaskInfo(SActivityInstance activityInstance) {
        Map<String, Object> info = new HashMap<>();
        info.put("name", activityInstance.getName());
        info.put("displayName", activityInstance.getDisplayName());
        info.put("completedOn", System.currentTimeMillis());
        return info;
    }

    private Object getProcessData(Long processInstanceId, String variableName) throws Exception {
        try {
            DataInstanceService dataInstanceService = getTenantServiceAccessor().getDataInstanceService();
            SDataInstance dataInstance = dataInstanceService.getLocalDataInstance(variableName, processInstanceId, DATA_CONTAINER);
            return dataInstance.getValue();
        }catch(SDataInstanceException sdie){
            throw new Exception ("Variable "+variableName + " could not be retrieved from process insntance " + processInstanceId+". Error: " +sdie.getMessage());
        }
    }

    @Override
    public boolean isInterested(SEvent sEvent) {
        try{
            Object eventObject = sEvent.getObject();
            if (eventObject instanceof SActivityInstance) {
                SActivityInstance activityInstance = (SActivityInstance) eventObject;
                if(sEvent.getType().equals(ACTIVITYINSTANCE_CLOSED)) {
                    if (activityInstance.getStateName().equals(ActivityStates.COMPLETED_STATE)) {
                        return true;
                    }
                }
            }
        }catch (Exception e){
            // In case of exception we write in the log
            if (technicalLoggerService.isLoggable(this.getClass(), technicalLogSeverity)) {
                technicalLoggerService.log(this.getClass(),ERROR_SEVERITY , "TaskCompleteEventHandler: ERROR evaluating event " + sEvent, e);
            }
            return false;
        }
        return false;
    }

    @Override
    public String getIdentifier() {
        //ensure this handler is registered only once
        return UUID.randomUUID().toString();
    }

    private TenantServiceAccessor getTenantServiceAccessor()
            throws SHandlerExecutionException {
        try {
            ServiceAccessorFactory serviceAccessorFactory = ServiceAccessorFactory.getInstance();
            return serviceAccessorFactory.createTenantServiceAccessor(tenantId);
        } catch (Exception e) {
            throw new SHandlerExecutionException(e.getMessage(), null);
        }
    }
}
