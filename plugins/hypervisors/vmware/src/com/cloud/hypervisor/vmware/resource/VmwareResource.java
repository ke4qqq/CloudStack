// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.hypervisor.vmware.resource;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.SocketChannel;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

import com.google.gson.Gson;
import com.vmware.vim25.AboutInfo;
import com.vmware.vim25.BoolPolicy;
import com.vmware.vim25.ClusterDasConfigInfo;
import com.vmware.vim25.ComputeResourceSummary;
import com.vmware.vim25.CustomFieldStringValue;
import com.vmware.vim25.DVPortConfigInfo;
import com.vmware.vim25.DVPortConfigSpec;
import com.vmware.vim25.DatastoreSummary;
import com.vmware.vim25.DistributedVirtualPort;
import com.vmware.vim25.DistributedVirtualSwitchPortConnection;
import com.vmware.vim25.DistributedVirtualSwitchPortCriteria;
import com.vmware.vim25.DynamicProperty;
import com.vmware.vim25.HostCapability;
import com.vmware.vim25.HostHostBusAdapter;
import com.vmware.vim25.HostInternetScsiHba;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectContent;
import com.vmware.vim25.OptionValue;
import com.vmware.vim25.PerfCounterInfo;
import com.vmware.vim25.PerfEntityMetric;
import com.vmware.vim25.PerfEntityMetricBase;
import com.vmware.vim25.PerfMetricId;
import com.vmware.vim25.PerfMetricIntSeries;
import com.vmware.vim25.PerfMetricSeries;
import com.vmware.vim25.PerfQuerySpec;
import com.vmware.vim25.PerfSampleInfo;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.ToolsUnavailableFaultMsg;
import com.vmware.vim25.VMwareDVSPortSetting;
import com.vmware.vim25.VimPortType;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceBackingInfo;
import com.vmware.vim25.VirtualDeviceConfigSpec;
import com.vmware.vim25.VirtualDeviceConfigSpecOperation;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualEthernetCardDistributedVirtualPortBackingInfo;
import com.vmware.vim25.VirtualEthernetCardNetworkBackingInfo;
import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.vim25.VirtualMachineGuestOsIdentifier;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.VirtualMachineRelocateSpec;
import com.vmware.vim25.VirtualMachineRelocateSpecDiskLocator;
import com.vmware.vim25.VirtualMachineRuntimeInfo;
import com.vmware.vim25.VmwareDistributedVirtualSwitchVlanIdSpec;

import org.apache.cloudstack.storage.command.StorageSubSystemCommand;
import org.apache.cloudstack.storage.to.TemplateObjectTO;
import org.apache.cloudstack.storage.to.VolumeObjectTO;

import com.cloud.agent.IAgentControl;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.AttachIsoCommand;
import com.cloud.agent.api.BackupSnapshotAnswer;
import com.cloud.agent.api.BackupSnapshotCommand;
import com.cloud.agent.api.CheckHealthAnswer;
import com.cloud.agent.api.CheckHealthCommand;
import com.cloud.agent.api.CheckNetworkAnswer;
import com.cloud.agent.api.CheckNetworkCommand;
import com.cloud.agent.api.CheckOnHostAnswer;
import com.cloud.agent.api.CheckOnHostCommand;
import com.cloud.agent.api.CheckVirtualMachineAnswer;
import com.cloud.agent.api.CheckVirtualMachineCommand;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.CreatePrivateTemplateFromSnapshotCommand;
import com.cloud.agent.api.CreatePrivateTemplateFromVolumeCommand;
import com.cloud.agent.api.CreateStoragePoolCommand;
import com.cloud.agent.api.CreateVMSnapshotAnswer;
import com.cloud.agent.api.CreateVMSnapshotCommand;
import com.cloud.agent.api.CreateVolumeFromSnapshotAnswer;
import com.cloud.agent.api.CreateVolumeFromSnapshotCommand;
import com.cloud.agent.api.DeleteStoragePoolCommand;
import com.cloud.agent.api.DeleteVMSnapshotAnswer;
import com.cloud.agent.api.DeleteVMSnapshotCommand;
import com.cloud.agent.api.GetHostStatsAnswer;
import com.cloud.agent.api.GetHostStatsCommand;
import com.cloud.agent.api.GetStorageStatsAnswer;
import com.cloud.agent.api.GetStorageStatsCommand;
import com.cloud.agent.api.GetVmDiskStatsAnswer;
import com.cloud.agent.api.GetVmDiskStatsCommand;
import com.cloud.agent.api.GetVmStatsAnswer;
import com.cloud.agent.api.GetVmStatsCommand;
import com.cloud.agent.api.GetVncPortAnswer;
import com.cloud.agent.api.GetVncPortCommand;
import com.cloud.agent.api.HostStatsEntry;
import com.cloud.agent.api.HostVmStateReportEntry;
import com.cloud.agent.api.MaintainAnswer;
import com.cloud.agent.api.MaintainCommand;
import com.cloud.agent.api.ManageSnapshotAnswer;
import com.cloud.agent.api.ManageSnapshotCommand;
import com.cloud.agent.api.MigrateAnswer;
import com.cloud.agent.api.MigrateCommand;
import com.cloud.agent.api.MigrateWithStorageAnswer;
import com.cloud.agent.api.MigrateWithStorageCommand;
import com.cloud.agent.api.ModifySshKeysCommand;
import com.cloud.agent.api.ModifyStoragePoolAnswer;
import com.cloud.agent.api.ModifyStoragePoolCommand;
import com.cloud.agent.api.NetworkUsageAnswer;
import com.cloud.agent.api.NetworkUsageCommand;
import com.cloud.agent.api.PingCommand;
import com.cloud.agent.api.PingRoutingCommand;
import com.cloud.agent.api.PingTestCommand;
import com.cloud.agent.api.PlugNicAnswer;
import com.cloud.agent.api.PlugNicCommand;
import com.cloud.agent.api.PrepareForMigrationAnswer;
import com.cloud.agent.api.PrepareForMigrationCommand;
import com.cloud.agent.api.PvlanSetupCommand;
import com.cloud.agent.api.ReadyAnswer;
import com.cloud.agent.api.ReadyCommand;
import com.cloud.agent.api.RebootAnswer;
import com.cloud.agent.api.RebootCommand;
import com.cloud.agent.api.RebootRouterCommand;
import com.cloud.agent.api.RevertToVMSnapshotAnswer;
import com.cloud.agent.api.RevertToVMSnapshotCommand;
import com.cloud.agent.api.ScaleVmAnswer;
import com.cloud.agent.api.ScaleVmCommand;
import com.cloud.agent.api.SetupAnswer;
import com.cloud.agent.api.SetupCommand;
import com.cloud.agent.api.SetupGuestNetworkCommand;
import com.cloud.agent.api.StartAnswer;
import com.cloud.agent.api.StartCommand;
import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.StartupRoutingCommand;
import com.cloud.agent.api.StartupStorageCommand;
import com.cloud.agent.api.StopAnswer;
import com.cloud.agent.api.StopCommand;
import com.cloud.agent.api.StoragePoolInfo;
import com.cloud.agent.api.UnPlugNicAnswer;
import com.cloud.agent.api.UnPlugNicCommand;
import com.cloud.agent.api.UnregisterNicCommand;
import com.cloud.agent.api.UnregisterVMCommand;
import com.cloud.agent.api.UpgradeSnapshotCommand;
import com.cloud.agent.api.ValidateSnapshotAnswer;
import com.cloud.agent.api.ValidateSnapshotCommand;
import com.cloud.agent.api.VmStatsEntry;
import com.cloud.agent.api.check.CheckSshAnswer;
import com.cloud.agent.api.check.CheckSshCommand;
import com.cloud.agent.api.routing.IpAssocCommand;
import com.cloud.agent.api.routing.IpAssocVpcCommand;
import com.cloud.agent.api.routing.NetworkElementCommand;
import com.cloud.agent.api.routing.SetNetworkACLCommand;
import com.cloud.agent.api.routing.SetSourceNatCommand;
import com.cloud.agent.api.storage.CopyVolumeAnswer;
import com.cloud.agent.api.storage.CopyVolumeCommand;
import com.cloud.agent.api.storage.CreatePrivateTemplateAnswer;
import com.cloud.agent.api.storage.DestroyCommand;
import com.cloud.agent.api.storage.MigrateVolumeAnswer;
import com.cloud.agent.api.storage.MigrateVolumeCommand;
import com.cloud.agent.api.storage.PrimaryStorageDownloadAnswer;
import com.cloud.agent.api.storage.PrimaryStorageDownloadCommand;
import com.cloud.agent.api.storage.ResizeVolumeAnswer;
import com.cloud.agent.api.storage.ResizeVolumeCommand;
import com.cloud.agent.api.to.DataStoreTO;
import com.cloud.agent.api.to.DiskTO;
import com.cloud.agent.api.to.IpAddressTO;
import com.cloud.agent.api.to.NfsTO;
import com.cloud.agent.api.to.NicTO;
import com.cloud.agent.api.to.StorageFilerTO;
import com.cloud.agent.api.to.VirtualMachineTO;
import com.cloud.agent.api.to.VolumeTO;
import com.cloud.agent.resource.virtualnetwork.VirtualRouterDeployer;
import com.cloud.agent.resource.virtualnetwork.VirtualRoutingResource;
import com.cloud.dc.DataCenter.NetworkType;
import com.cloud.dc.Vlan;
import com.cloud.exception.CloudException;
import com.cloud.exception.InternalErrorException;
import com.cloud.host.Host.Type;
import com.cloud.hypervisor.guru.VMwareGuru;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.hypervisor.vmware.manager.VmwareHostService;
import com.cloud.hypervisor.vmware.manager.VmwareManager;
import com.cloud.hypervisor.vmware.manager.VmwareStorageMount;
import com.cloud.hypervisor.vmware.mo.ClusterMO;
import com.cloud.hypervisor.vmware.mo.CustomFieldConstants;
import com.cloud.hypervisor.vmware.mo.CustomFieldsManagerMO;
import com.cloud.hypervisor.vmware.mo.DatacenterMO;
import com.cloud.hypervisor.vmware.mo.DatastoreFile;
import com.cloud.hypervisor.vmware.mo.DatastoreMO;
import com.cloud.hypervisor.vmware.mo.DiskControllerType;
import com.cloud.hypervisor.vmware.mo.FeatureKeyConstants;
import com.cloud.hypervisor.vmware.mo.HostMO;
import com.cloud.hypervisor.vmware.mo.HostStorageSystemMO;
import com.cloud.hypervisor.vmware.mo.HypervisorHostHelper;
import com.cloud.hypervisor.vmware.mo.NetworkDetails;
import com.cloud.hypervisor.vmware.mo.TaskMO;
import com.cloud.hypervisor.vmware.mo.VirtualEthernetCardType;
import com.cloud.hypervisor.vmware.mo.VirtualMachineDiskInfo;
import com.cloud.hypervisor.vmware.mo.VirtualMachineDiskInfoBuilder;
import com.cloud.hypervisor.vmware.mo.VirtualMachineMO;
import com.cloud.hypervisor.vmware.mo.VirtualSwitchType;
import com.cloud.hypervisor.vmware.mo.VmwareHypervisorHost;
import com.cloud.hypervisor.vmware.mo.VmwareHypervisorHostNetworkSummary;
import com.cloud.hypervisor.vmware.mo.VmwareHypervisorHostResourceSummary;
import com.cloud.hypervisor.vmware.util.VmwareContext;
import com.cloud.hypervisor.vmware.util.VmwareContextPool;
import com.cloud.hypervisor.vmware.util.VmwareHelper;
import com.cloud.network.Networks;
import com.cloud.network.Networks.BroadcastDomainType;
import com.cloud.network.Networks.TrafficType;
import com.cloud.network.VmwareTrafficLabel;
import com.cloud.resource.ServerResource;
import com.cloud.serializer.GsonHelper;
import com.cloud.storage.Storage;
import com.cloud.storage.Storage.StoragePoolType;
import com.cloud.storage.Volume;
import com.cloud.storage.resource.StoragePoolResource;
import com.cloud.storage.resource.StorageSubsystemCommandHandler;
import com.cloud.storage.resource.VmwareStorageLayoutHelper;
import com.cloud.storage.resource.VmwareStorageProcessor;
import com.cloud.storage.resource.VmwareStorageSubsystemCommandHandler;
import com.cloud.storage.template.TemplateProp;
import com.cloud.utils.DateUtil;
import com.cloud.utils.ExecutionResult;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.Ternary;
import com.cloud.utils.db.DB;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.exception.ExceptionUtil;
import com.cloud.utils.mgmt.JmxUtil;
import com.cloud.utils.mgmt.PropertyMapDynamicBean;
import com.cloud.utils.net.NetUtils;
import com.cloud.utils.ssh.SshHelper;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachine.PowerState;
import com.cloud.vm.VirtualMachineName;
import com.cloud.vm.VmDetailConstants;

public class VmwareResource implements StoragePoolResource, ServerResource, VmwareHostService, VirtualRouterDeployer {
    private static final Logger s_logger = Logger.getLogger(VmwareResource.class);

    protected String _name;

    protected final long _opsTimeout = 900000;   // 15 minutes time out to time

    protected final int _shutdownWaitMs = 300000;  // wait up to 5 minutes for shutdown

    // out an operation
    protected final int _retry = 24;
    protected final int _sleep = 10000;
    protected final int DefaultDomRSshPort = 3922;
    protected final int MazCmdMBean = 100;

    protected String _url;
    protected String _dcId;
    protected String _pod;
    protected String _cluster;
    protected String _username;
    protected String _password;
    protected String _guid;
    protected String _vCenterAddress;

    protected String _privateNetworkVSwitchName;
    protected VmwareTrafficLabel _guestTrafficInfo = new VmwareTrafficLabel(TrafficType.Guest);
    protected VmwareTrafficLabel _publicTrafficInfo = new VmwareTrafficLabel(TrafficType.Public);
    protected Map<String, String> _vsmCredentials = null;
    protected int _portsPerDvPortGroup;
    protected boolean _fullCloneFlag = false;
    protected boolean _instanceNameFlag = false;

    protected boolean _recycleHungWorker = false;
    protected DiskControllerType _rootDiskController = DiskControllerType.ide;

    protected ManagedObjectReference _morHyperHost;
    protected static ThreadLocal<VmwareContext> s_serviceContext = new ThreadLocal<VmwareContext>();
    protected String _hostName;

    protected List<PropertyMapDynamicBean> _cmdMBeans = new ArrayList<PropertyMapDynamicBean>();

    protected Gson _gson;

    protected volatile long _cmdSequence = 1;

    protected StorageSubsystemCommandHandler storageHandler;
    private VmwareStorageProcessor _storageProcessor;

    protected VirtualRoutingResource _vrResource;

    protected static HashMap<VirtualMachinePowerState, PowerState> s_powerStatesTable;
    static {
        s_powerStatesTable = new HashMap<VirtualMachinePowerState, PowerState>();
        s_powerStatesTable.put(VirtualMachinePowerState.POWERED_ON, PowerState.PowerOn);
        s_powerStatesTable.put(VirtualMachinePowerState.POWERED_OFF, PowerState.PowerOff);
        s_powerStatesTable.put(VirtualMachinePowerState.SUSPENDED, PowerState.PowerOn);
    }

    public Gson getGson() {
        return _gson;
    }

    public VmwareResource() {
        _gson = GsonHelper.getGsonLogger();
    }

    private String getCommandLogTitle(Command cmd) {
        StringBuffer sb = new StringBuffer();
        if (_hostName != null) {
            sb.append(_hostName);
        }

        if (cmd.getContextParam("job") != null) {
            sb.append(", ").append(cmd.getContextParam("job"));
        }
        sb.append(", cmd: ").append(cmd.getClass().getSimpleName());

        return sb.toString();
    }

    @Override
    public Answer executeRequest(Command cmd) {

        if (s_logger.isTraceEnabled())
            s_logger.trace("Begin executeRequest(), cmd: " + cmd.getClass().getSimpleName());

        Answer answer = null;
        NDC.push(getCommandLogTitle(cmd));
        try {
            long cmdSequence = _cmdSequence++;
            Date startTime = DateUtil.currentGMTTime();
            PropertyMapDynamicBean mbean = new PropertyMapDynamicBean();
            mbean.addProp("StartTime", DateUtil.getDateDisplayString(TimeZone.getDefault(), startTime));
            mbean.addProp("Command", _gson.toJson(cmd));
            mbean.addProp("Sequence", String.valueOf(cmdSequence));
            mbean.addProp("Name", cmd.getClass().getSimpleName());

            Class<? extends Command> clz = cmd.getClass();
            if (cmd instanceof NetworkElementCommand) {
                return _vrResource.executeRequest((NetworkElementCommand)cmd);
            } else if (clz == ReadyCommand.class) {
                answer = execute((ReadyCommand)cmd);
            } else if (clz == GetHostStatsCommand.class) {
                answer = execute((GetHostStatsCommand)cmd);
            } else if (clz == GetVmStatsCommand.class) {
                answer = execute((GetVmStatsCommand)cmd);
            } else if (clz == GetVmDiskStatsCommand.class) {
                answer = execute((GetVmDiskStatsCommand)cmd);
            } else if (clz == CheckHealthCommand.class) {
                answer = execute((CheckHealthCommand)cmd);
            } else if (clz == StopCommand.class) {
                answer = execute((StopCommand)cmd);
            } else if (clz == RebootRouterCommand.class) {
                answer = execute((RebootRouterCommand)cmd);
            } else if (clz == RebootCommand.class) {
                answer = execute((RebootCommand)cmd);
            } else if (clz == CheckVirtualMachineCommand.class) {
                answer = execute((CheckVirtualMachineCommand)cmd);
            } else if (clz == PrepareForMigrationCommand.class) {
                answer = execute((PrepareForMigrationCommand)cmd);
            } else if (clz == MigrateCommand.class) {
                answer = execute((MigrateCommand)cmd);
            } else if (clz == MigrateWithStorageCommand.class) {
                answer = execute((MigrateWithStorageCommand)cmd);
            } else if (clz == MigrateVolumeCommand.class) {
                answer = execute((MigrateVolumeCommand)cmd);
            } else if (clz == DestroyCommand.class) {
                answer = execute((DestroyCommand)cmd);
            } else if (clz == CreateStoragePoolCommand.class) {
                return execute((CreateStoragePoolCommand)cmd);
            } else if (clz == ModifyStoragePoolCommand.class) {
                answer = execute((ModifyStoragePoolCommand)cmd);
            } else if (clz == DeleteStoragePoolCommand.class) {
                answer = execute((DeleteStoragePoolCommand)cmd);
            } else if (clz == CopyVolumeCommand.class) {
                answer = execute((CopyVolumeCommand)cmd);
            } else if (clz == AttachIsoCommand.class) {
                answer = execute((AttachIsoCommand)cmd);
            } else if (clz == ValidateSnapshotCommand.class) {
                answer = execute((ValidateSnapshotCommand)cmd);
            } else if (clz == ManageSnapshotCommand.class) {
                answer = execute((ManageSnapshotCommand)cmd);
            } else if (clz == BackupSnapshotCommand.class) {
                answer = execute((BackupSnapshotCommand)cmd);
            } else if (clz == CreateVolumeFromSnapshotCommand.class) {
                answer = execute((CreateVolumeFromSnapshotCommand)cmd);
            } else if (clz == CreatePrivateTemplateFromVolumeCommand.class) {
                answer = execute((CreatePrivateTemplateFromVolumeCommand)cmd);
            } else if (clz == CreatePrivateTemplateFromSnapshotCommand.class) {
                answer = execute((CreatePrivateTemplateFromSnapshotCommand)cmd);
            } else if (clz == UpgradeSnapshotCommand.class) {
                answer = execute((UpgradeSnapshotCommand)cmd);
            } else if (clz == GetStorageStatsCommand.class) {
                answer = execute((GetStorageStatsCommand)cmd);
            } else if (clz == PrimaryStorageDownloadCommand.class) {
                answer = execute((PrimaryStorageDownloadCommand)cmd);
            } else if (clz == GetVncPortCommand.class) {
                answer = execute((GetVncPortCommand)cmd);
            } else if (clz == SetupCommand.class) {
                answer = execute((SetupCommand)cmd);
            } else if (clz == MaintainCommand.class) {
                answer = execute((MaintainCommand)cmd);
            } else if (clz == PingTestCommand.class) {
                answer = execute((PingTestCommand)cmd);
            } else if (clz == CheckOnHostCommand.class) {
                answer = execute((CheckOnHostCommand)cmd);
            } else if (clz == ModifySshKeysCommand.class) {
                answer = execute((ModifySshKeysCommand)cmd);
            } else if (clz == NetworkUsageCommand.class) {
                answer = execute((NetworkUsageCommand)cmd);
            } else if (clz == StartCommand.class) {
                answer = execute((StartCommand)cmd);
            } else if (clz == CheckSshCommand.class) {
                answer = execute((CheckSshCommand)cmd);
            } else if (clz == CheckNetworkCommand.class) {
                answer = execute((CheckNetworkCommand)cmd);
            } else if (clz == PlugNicCommand.class) {
                answer = execute((PlugNicCommand)cmd);
            } else if (clz == UnPlugNicCommand.class) {
                answer = execute((UnPlugNicCommand)cmd);
            } else if (cmd instanceof CreateVMSnapshotCommand) {
                return execute((CreateVMSnapshotCommand)cmd);
            } else if (cmd instanceof DeleteVMSnapshotCommand) {
                return execute((DeleteVMSnapshotCommand)cmd);
            } else if (cmd instanceof RevertToVMSnapshotCommand) {
                return execute((RevertToVMSnapshotCommand)cmd);
            } else if (clz == ResizeVolumeCommand.class) {
                return execute((ResizeVolumeCommand)cmd);
            } else if (clz == UnregisterVMCommand.class) {
                return execute((UnregisterVMCommand) cmd);
            } else if (cmd instanceof StorageSubSystemCommand) {
                return storageHandler.handleStorageCommands((StorageSubSystemCommand) cmd);
            } else if (clz == ScaleVmCommand.class) {
                return execute((ScaleVmCommand)cmd);
            } else if (clz == PvlanSetupCommand.class) {
                return execute((PvlanSetupCommand)cmd);
            } else if (clz == UnregisterNicCommand.class) {
                answer = execute((UnregisterNicCommand)cmd);
            } else {
                answer = Answer.createUnsupportedCommandAnswer(cmd);
            }

            if (cmd.getContextParam("checkpoint") != null) {
                answer.setContextParam("checkpoint", cmd.getContextParam("checkpoint"));
            }

            Date doneTime = DateUtil.currentGMTTime();
            mbean.addProp("DoneTime", DateUtil.getDateDisplayString(TimeZone.getDefault(), doneTime));
            mbean.addProp("Answer", _gson.toJson(answer));

            synchronized (this) {
                try {
                    JmxUtil.registerMBean("VMware " + _morHyperHost.getValue(), "Command " + cmdSequence + "-" + cmd.getClass().getSimpleName(), mbean);
                    _cmdMBeans.add(mbean);

                    if (_cmdMBeans.size() >= MazCmdMBean) {
                        PropertyMapDynamicBean mbeanToRemove = _cmdMBeans.get(0);
                        _cmdMBeans.remove(0);

                        JmxUtil.unregisterMBean("VMware " + _morHyperHost.getValue(),
                                "Command " + mbeanToRemove.getProp("Sequence") + "-" + mbeanToRemove.getProp("Name"));
                    }
                } catch (Exception e) {
                    if (s_logger.isTraceEnabled())
                        s_logger.trace("Unable to register JMX monitoring due to exception " + ExceptionUtil.toString(e));
                }
            }

        } finally {
            recycleServiceContext();
            NDC.pop();
        }

        if (s_logger.isTraceEnabled())
            s_logger.trace("End executeRequest(), cmd: " + cmd.getClass().getSimpleName());

        return answer;
    }

    /**
     * Registers the vm to the inventory given the vmx file.
     */
    private void registerVm(String vmName, DatastoreMO dsMo) throws Exception{

        //1st param
        VmwareHypervisorHost hyperHost = getHyperHost(getServiceContext());
        ManagedObjectReference dcMor = hyperHost.getHyperHostDatacenter();
        DatacenterMO dataCenterMo = new DatacenterMO(getServiceContext(), dcMor);
        ManagedObjectReference vmFolderMor = dataCenterMo.getVmFolder();

        //2nd param
        String vmxFilePath = dsMo.searchFileInSubFolders(vmName + ".vmx", false);

        // 5th param
        ManagedObjectReference morPool = hyperHost.getHyperHostOwnerResourcePool();

        ManagedObjectReference morTask = getServiceContext().getService().registerVMTask(vmFolderMor, vmxFilePath, vmName, false, morPool, hyperHost.getMor());
        boolean result = getServiceContext().getVimClient().waitForTask(morTask);
        if (!result) {
            throw new Exception("Unable to register vm due to " + TaskMO.getTaskFailureInfo(getServiceContext(), morTask));
        } else {
            getServiceContext().waitForTaskProgressDone(morTask);
        }

    }

    private Answer execute(ResizeVolumeCommand cmd) {
        String path = cmd.getPath();
        String vmName = cmd.getInstanceName();
        long newSize = cmd.getNewSize() / 1024;
        long oldSize = cmd.getCurrentSize()/1024;
        boolean useWorkerVm = false;

        VmwareHypervisorHost hyperHost = getHyperHost(getServiceContext());
        String poolId = cmd.getPoolUuid();
        VirtualMachineMO vmMo = null;
        DatastoreMO dsMo = null;
        ManagedObjectReference morDS = null;
        String vmdkDataStorePath = null;

        try {
            if (newSize < oldSize) {
                throw new Exception("VMware doesn't support shrinking volume from larger size: " + oldSize/(1024*1024) + " GB to a smaller size: " + newSize/(1024*1024) + " GB");
            } else if (newSize == oldSize) {
                return new ResizeVolumeAnswer(cmd, true, "success", newSize*1024);
            }
            if (vmName.equalsIgnoreCase("none")) {
                // we need to spawn a worker VM to attach the volume to and
                // resize the volume.
                useWorkerVm = true;
                vmName = this.getWorkerName(getServiceContext(), cmd, 0);

                morDS = HypervisorHostHelper.findDatastoreWithBackwardsCompatibility(hyperHost, poolId);
                dsMo = new DatastoreMO(hyperHost.getContext(), morDS);
                s_logger.info("Create worker VM " + vmName);
                vmMo = HypervisorHostHelper.createWorkerVM(hyperHost, dsMo, vmName);
                if (vmMo == null) {
                    throw new Exception("Unable to create a worker VM for volume resize");
                }

                synchronized (this) {
                    vmdkDataStorePath = VmwareStorageLayoutHelper.getLegacyDatastorePathFromVmdkFileName(dsMo, path + ".vmdk");
                    vmMo.attachDisk(new String[] { vmdkDataStorePath }, morDS);
                }
            }
            // find VM through datacenter (VM is not at the target host yet)
            vmMo = hyperHost.findVmOnPeerHyperHost(vmName);
            if (vmMo == null) {
                String msg = "VM " + vmName + " does not exist in VMware datacenter";
                s_logger.error(msg);
                throw new Exception(msg);
            }

            Pair<VirtualDisk, String> vdisk = vmMo.getDiskDevice(path, false);
            if (vdisk == null) {
                if (s_logger.isTraceEnabled())
                    s_logger.trace("resize volume done (failed)");
                throw new Exception("No such disk device: " + path);
            }
            VirtualDisk disk = vdisk.first();
            disk.setCapacityInKB(newSize);

            VirtualMachineConfigSpec vmConfigSpec = new VirtualMachineConfigSpec();
            VirtualDeviceConfigSpec deviceConfigSpec = new VirtualDeviceConfigSpec();
            deviceConfigSpec.setDevice(disk);
            deviceConfigSpec.setOperation(VirtualDeviceConfigSpecOperation.EDIT);
            vmConfigSpec.getDeviceChange().add(deviceConfigSpec);
            if (!vmMo.configureVm(vmConfigSpec)) {
                throw new Exception("Failed to configure VM to resize disk. vmName: " + vmName);
            }

            return new ResizeVolumeAnswer(cmd, true, "success", newSize * 1024);
        } catch (Exception e) {
            s_logger.error("Unable to resize volume", e);
            String error = "Failed to resize volume: " + e.getMessage();
            return new ResizeVolumeAnswer(cmd, false, error);
        } finally {
            try {
                if (useWorkerVm == true) {
                    s_logger.info("Destroy worker VM after volume resize");
                    vmMo.detachDisk(vmdkDataStorePath, false);
                    vmMo.destroy();
                }
            } catch (Throwable e) {
                s_logger.info("Failed to destroy worker VM: " + vmName);
            }
        }
    }

    protected Answer execute(CheckNetworkCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource CheckNetworkCommand " + _gson.toJson(cmd));
        }

        // TODO setup portgroup for private network needs to be done here now
        return new CheckNetworkAnswer(cmd, true, "Network Setup check by names is done");
    }

    protected Answer execute(NetworkUsageCommand cmd) {
        if (cmd.isForVpc()) {
            return VPCNetworkUsage(cmd);
        }
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource NetworkUsageCommand " + _gson.toJson(cmd));
        }
        if (cmd.getOption() != null && cmd.getOption().equals("create")) {
            String result = networkUsage(cmd.getPrivateIP(), "create", null);
            NetworkUsageAnswer answer = new NetworkUsageAnswer(cmd, result, 0L, 0L);
            return answer;
        }
        long[] stats = getNetworkStats(cmd.getPrivateIP());

        NetworkUsageAnswer answer = new NetworkUsageAnswer(cmd, "", stats[0], stats[1]);
        return answer;
    }

    protected NetworkUsageAnswer VPCNetworkUsage(NetworkUsageCommand cmd) {
        String privateIp = cmd.getPrivateIP();
        String option = cmd.getOption();
        String publicIp = cmd.getGatewayIP();

        String args = "-l " + publicIp + " ";
        if (option.equals("get")) {
            args += "-g";
        } else if (option.equals("create")) {
            args += "-c";
            String vpcCIDR = cmd.getVpcCIDR();
            args += " -v " + vpcCIDR;
        } else if (option.equals("reset")) {
            args += "-r";
        } else if (option.equals("vpn")) {
            args += "-n";
        } else if (option.equals("remove")) {
            args += "-d";
        } else {
            return new NetworkUsageAnswer(cmd, "success", 0L, 0L);
        }

        ExecutionResult callResult = executeInVR(privateIp, "vpc_netusage.sh", args);

        if (!callResult.isSuccess()) {
            s_logger.error("Unable to execute NetworkUsage command on DomR (" + privateIp + "), domR may not be ready yet. failure due to " + callResult.getDetails());
        }

        if (option.equals("get") || option.equals("vpn")) {
            String result = callResult.getDetails();
            if (result == null || result.isEmpty()) {
                s_logger.error(" vpc network usage get returns empty ");
            }
            long[] stats = new long[2];
            if (result != null) {
                String[] splitResult = result.split(":");
                int i = 0;
                while (i < splitResult.length - 1) {
                    stats[0] += (new Long(splitResult[i++])).longValue();
                    stats[1] += (new Long(splitResult[i++])).longValue();
                }
                return new NetworkUsageAnswer(cmd, "success", stats[0], stats[1]);
            }
        }
        return new NetworkUsageAnswer(cmd, "success", 0L, 0L);
    }

    @Override
    public ExecutionResult createFileInVR(String routerIp, String filePath, String fileName, String content) {
        VmwareManager mgr = getServiceContext().getStockObject(VmwareManager.CONTEXT_STOCK_NAME);
        File keyFile = mgr.getSystemVMKeyFile();
        try {
            SshHelper.scpTo(routerIp, 3922, "root", keyFile, null, filePath, content.getBytes(), fileName, null);
        } catch (Exception e) {
            s_logger.warn("Fail to create file " + filePath + fileName + " in VR " + routerIp, e);
            return new ExecutionResult(false, e.getMessage());
        }
        return new ExecutionResult(true, null);
    }

    @Override
    public ExecutionResult prepareCommand(NetworkElementCommand cmd) {
        //Update IP used to access router
        cmd.setRouterAccessIp(getRouterSshControlIp(cmd));
        assert cmd.getRouterAccessIp() != null;

        if (cmd instanceof IpAssocVpcCommand) {
            return prepareNetworkElementCommand((IpAssocVpcCommand)cmd);
        } else if (cmd instanceof IpAssocCommand) {
            return prepareNetworkElementCommand((IpAssocCommand)cmd);
        } else if (cmd instanceof SetSourceNatCommand) {
            return prepareNetworkElementCommand((SetSourceNatCommand)cmd);
        } else if (cmd instanceof SetupGuestNetworkCommand) {
            return prepareNetworkElementCommand((SetupGuestNetworkCommand)cmd);
        } else if (cmd instanceof SetNetworkACLCommand) {
            return prepareNetworkElementCommand((SetNetworkACLCommand)cmd);
        }
        return new ExecutionResult(true, null);
    }

    @Override
    public ExecutionResult cleanupCommand(NetworkElementCommand cmd) {
        return new ExecutionResult(true, null);
    }

    //
    // list IP with eth devices
    //  ifconfig ethx |grep -B1 "inet addr" | awk '{ if ( $1 == "inet" ) { print $2 } else if ( $2 == "Link" ) { printf "%s:" ,$1 } }'
    //     | awk -F: '{ print $1 ": " $3 }'
    //
    // returns
    //      eth0:xx.xx.xx.xx
    //
    //
    private int findRouterEthDeviceIndex(String domrName, String routerIp, String mac) throws Exception {
        VmwareManager mgr = getServiceContext().getStockObject(VmwareManager.CONTEXT_STOCK_NAME);

        s_logger.info("findRouterEthDeviceIndex. mac: " + mac);

        // TODO : this is a temporary very inefficient solution, will refactor it later
        Pair<Boolean, String> result = SshHelper.sshExecute(routerIp, DefaultDomRSshPort, "root", mgr.getSystemVMKeyFile(), null, "ls /proc/sys/net/ipv4/conf");

        // when we dynamically plug in a new NIC into virtual router, it may take time to show up in guest OS
        // we use a waiting loop here as a workaround to synchronize activities in systems
        long startTick = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTick < 15000) {
            if (result.first()) {
                String[] tokens = result.second().split("\\s+");
                for (String token : tokens) {
                    if (!("all".equalsIgnoreCase(token) || "default".equalsIgnoreCase(token) || "lo".equalsIgnoreCase(token))) {
                        String cmd = String.format("ip address show %s | grep link/ether | sed -e 's/^[ \t]*//' | cut -d' ' -f2", token);

                        if (s_logger.isDebugEnabled())
                            s_logger.debug("Run domr script " + cmd);
                        Pair<Boolean, String> result2 = SshHelper.sshExecute(routerIp, DefaultDomRSshPort, "root", mgr.getSystemVMKeyFile(), null,
                                // TODO need to find the dev index inside router based on IP address
                                cmd);
                        if (s_logger.isDebugEnabled())
                            s_logger.debug("result: " + result2.first() + ", output: " + result2.second());

                        if (result2.first() && result2.second().trim().equalsIgnoreCase(mac.trim()))
                            return Integer.parseInt(token.substring(3));
                    }
                }
            }

            s_logger.warn("can not find intereface associated with mac: " + mac + ", guest OS may still at loading state, retry...");

            try {
                Thread.currentThread();
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }

        return -1;
    }

    private VirtualDevice findVirtualNicDevice(VirtualMachineMO vmMo, String mac) throws Exception {

        VirtualDevice[] nics = vmMo.getNicDevices();
        for (VirtualDevice nic : nics) {
            if (nic instanceof VirtualEthernetCard) {
                if (((VirtualEthernetCard)nic).getMacAddress().equals(mac))
                    return nic;
            }
        }
        return null;
    }

    protected ExecutionResult prepareNetworkElementCommand(SetupGuestNetworkCommand cmd) {
        NicTO nic = cmd.getNic();
        String routerIp = getRouterSshControlIp(cmd);
        String domrName =
                cmd.getAccessDetail(NetworkElementCommand.ROUTER_NAME);

        try {
            int ethDeviceNum = findRouterEthDeviceIndex(domrName, routerIp,
                    nic.getMac());
            nic.setDeviceId(ethDeviceNum);
        } catch (Exception e) {
            String msg = "Prepare SetupGuestNetwork failed due to " + e.toString();
            s_logger.warn(msg, e);
            return new ExecutionResult(false, msg);
        }
        return new ExecutionResult(true, null);
    }


    private ExecutionResult prepareNetworkElementCommand(IpAssocVpcCommand cmd) {
        String routerName = cmd.getAccessDetail(NetworkElementCommand.ROUTER_NAME);
        String routerIp = getRouterSshControlIp(cmd);

        try {
            IpAddressTO[] ips = cmd.getIpAddresses();
            for (IpAddressTO ip : ips) {

                int ethDeviceNum = findRouterEthDeviceIndex(routerName, routerIp, ip.getVifMacAddress());
                if (ethDeviceNum < 0) {
                    if (ip.isAdd()) {
                        throw new InternalErrorException("Failed to find DomR VIF to associate/disassociate IP with.");
                    } else {
                        s_logger.debug("VIF to deassociate IP with does not exist, return success");
                        continue;
                    }
                }

                ip.setNicDevId(ethDeviceNum);
            }
        } catch (Exception e) {
            s_logger.error("Prepare Ip Assoc failure on applying one ip due to exception:  ", e);
            return new ExecutionResult(false, e.toString());
        }

        return new ExecutionResult(true, null);
    }

    protected ExecutionResult prepareNetworkElementCommand(SetSourceNatCommand cmd) {
        String routerName = cmd.getAccessDetail(NetworkElementCommand.ROUTER_NAME);
        String routerIp = getRouterSshControlIp(cmd);
        IpAddressTO pubIp = cmd.getIpAddress();

        try {
            int ethDeviceNum = findRouterEthDeviceIndex(routerName, routerIp, pubIp.getVifMacAddress());
            pubIp.setNicDevId(ethDeviceNum);
        } catch (Exception e) {
            String msg = "Prepare Ip SNAT failure due to " + e.toString();
            s_logger.error(msg, e);
            return new ExecutionResult(false, e.toString());
        }
        return new ExecutionResult(true, null);
    }

    private ExecutionResult prepareNetworkElementCommand(SetNetworkACLCommand cmd) {
        NicTO nic = cmd.getNic();
        String routerName =
                cmd.getAccessDetail(NetworkElementCommand.ROUTER_NAME);
        String routerIp = getRouterSshControlIp(cmd);

        try {
            int ethDeviceNum = findRouterEthDeviceIndex(routerName, routerIp,
                    nic.getMac());
            nic.setDeviceId(ethDeviceNum);
        } catch (Exception e) {
            String msg = "Prepare SetNetworkACL failed due to " + e.toString();
            s_logger.error(msg, e);
            return new ExecutionResult(false, msg);
        }
        return new ExecutionResult(true, null);
    }

    private PlugNicAnswer execute(PlugNicCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource PlugNicCommand " + _gson.toJson(cmd));
        }

        getServiceContext().getStockObject(VmwareManager.CONTEXT_STOCK_NAME);
        VmwareContext context = getServiceContext();
        try {
            VmwareHypervisorHost hyperHost = getHyperHost(context);

            String vmName = cmd.getVmName();
            VirtualMachineMO vmMo = hyperHost.findVmOnHyperHost(vmName);

            if (vmMo == null) {
                if (hyperHost instanceof HostMO) {
                    ClusterMO clusterMo = new ClusterMO(hyperHost.getContext(), ((HostMO)hyperHost).getParentMor());
                    vmMo = clusterMo.findVmOnHyperHost(vmName);
                }
            }

            if (vmMo == null) {
                String msg = "Router " + vmName + " no longer exists to execute PlugNic command";
                s_logger.error(msg);
                throw new Exception(msg);
            }

            /*
            if(!isVMWareToolsInstalled(vmMo)){
                String errMsg = "vmware tools is not installed or not running, cannot add nic to vm " + vmName;
                s_logger.debug(errMsg);
                return new PlugNicAnswer(cmd, false, "Unable to execute PlugNicCommand due to " + errMsg);
            }
             */
            // TODO need a way to specify the control of NIC device type
            VirtualEthernetCardType nicDeviceType = VirtualEthernetCardType.E1000;

            // find a usable device number in VMware environment
            VirtualDevice[] nicDevices = vmMo.getNicDevices();
            int deviceNumber = -1;
            for (VirtualDevice device : nicDevices) {
                if (device.getUnitNumber() > deviceNumber)
                    deviceNumber = device.getUnitNumber();
            }
            deviceNumber++;

            NicTO nicTo = cmd.getNic();
            VirtualDevice nic;
            Pair<ManagedObjectReference, String> networkInfo = prepareNetworkFromNicInfo(vmMo.getRunningHost(), nicTo, false, cmd.getVMType());
            if (VmwareHelper.isDvPortGroup(networkInfo.first())) {
                String dvSwitchUuid;
                ManagedObjectReference dcMor = hyperHost.getHyperHostDatacenter();
                DatacenterMO dataCenterMo = new DatacenterMO(context, dcMor);
                ManagedObjectReference dvsMor = dataCenterMo.getDvSwitchMor(networkInfo.first());
                dvSwitchUuid = dataCenterMo.getDvSwitchUuid(dvsMor);
                s_logger.info("Preparing NIC device on dvSwitch : " + dvSwitchUuid);
                nic =
                        VmwareHelper.prepareDvNicDevice(vmMo, networkInfo.first(), nicDeviceType, networkInfo.second(), dvSwitchUuid, nicTo.getMac(), deviceNumber,
                                deviceNumber + 1, true, true);
            } else {
                s_logger.info("Preparing NIC device on network " + networkInfo.second());
                nic =
                        VmwareHelper.prepareNicDevice(vmMo, networkInfo.first(), nicDeviceType, networkInfo.second(), nicTo.getMac(), deviceNumber, deviceNumber + 1, true,
                                true);
            }

            VirtualMachineConfigSpec vmConfigSpec = new VirtualMachineConfigSpec();
            //VirtualDeviceConfigSpec[] deviceConfigSpecArray = new VirtualDeviceConfigSpec[1];
            VirtualDeviceConfigSpec deviceConfigSpec = new VirtualDeviceConfigSpec();
            deviceConfigSpec.setDevice(nic);
            deviceConfigSpec.setOperation(VirtualDeviceConfigSpecOperation.ADD);

            vmConfigSpec.getDeviceChange().add(deviceConfigSpec);
            setNuageVspVrIpInExtraConfig(vmConfigSpec.getExtraConfig(), nicTo);
            if (!vmMo.configureVm(vmConfigSpec)) {
                throw new Exception("Failed to configure devices when running PlugNicCommand");
            }

            return new PlugNicAnswer(cmd, true, "success");
        } catch (Exception e) {
            s_logger.error("Unexpected exception: ", e);
            return new PlugNicAnswer(cmd, false, "Unable to execute PlugNicCommand due to " + e.toString());
        }
    }

    private UnPlugNicAnswer execute(UnPlugNicCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource UnPlugNicCommand " + _gson.toJson(cmd));
        }

        VmwareContext context = getServiceContext();
        try {
            VmwareHypervisorHost hyperHost = getHyperHost(context);

            String vmName = cmd.getVmName();
            VirtualMachineMO vmMo = hyperHost.findVmOnHyperHost(vmName);

            if (vmMo == null) {
                if (hyperHost instanceof HostMO) {
                    ClusterMO clusterMo = new ClusterMO(hyperHost.getContext(), ((HostMO)hyperHost).getParentMor());
                    vmMo = clusterMo.findVmOnHyperHost(vmName);
                }
            }

            if (vmMo == null) {
                String msg = "VM " + vmName + " no longer exists to execute UnPlugNic command";
                s_logger.error(msg);
                throw new Exception(msg);
            }

            /*
            if(!isVMWareToolsInstalled(vmMo)){
                String errMsg = "vmware tools not installed or not running, cannot remove nic from vm " + vmName;
                s_logger.debug(errMsg);
                return new UnPlugNicAnswer(cmd, false, "Unable to execute unPlugNicCommand due to " + errMsg);
            }
             */
            VirtualDevice nic = findVirtualNicDevice(vmMo, cmd.getNic().getMac());
            if (nic == null) {
                return new UnPlugNicAnswer(cmd, true, "success");
            }
            VirtualMachineConfigSpec vmConfigSpec = new VirtualMachineConfigSpec();
            //VirtualDeviceConfigSpec[] deviceConfigSpecArray = new VirtualDeviceConfigSpec[1];
            VirtualDeviceConfigSpec deviceConfigSpec = new VirtualDeviceConfigSpec();
            deviceConfigSpec.setDevice(nic);
            deviceConfigSpec.setOperation(VirtualDeviceConfigSpecOperation.REMOVE);

            vmConfigSpec.getDeviceChange().add(deviceConfigSpec);
            if (!vmMo.configureVm(vmConfigSpec)) {
                throw new Exception("Failed to configure devices when running unplugNicCommand");
            }

            return new UnPlugNicAnswer(cmd, true, "success");
        } catch (Exception e) {
            s_logger.error("Unexpected exception: ", e);
            return new UnPlugNicAnswer(cmd, false, "Unable to execute unPlugNicCommand due to " + e.toString());
        }
    }

    private void plugPublicNic(VirtualMachineMO vmMo, final String vlanId, final String vifMacAddress) throws Exception {
        // TODO : probably need to set traffic shaping
        Pair<ManagedObjectReference, String> networkInfo = null;
        VirtualSwitchType vSwitchType = VirtualSwitchType.StandardVirtualSwitch;
        if (_publicTrafficInfo != null) {
            vSwitchType = _publicTrafficInfo.getVirtualSwitchType();
        }
        /** FIXME We have no clue which network this nic is on and that means that we can't figure out the BroadcastDomainType
         *  so we assume that it's VLAN for now
         */
        if (VirtualSwitchType.StandardVirtualSwitch == vSwitchType) {
            synchronized (vmMo.getRunningHost().getMor().getValue().intern()) {
                networkInfo =
                        HypervisorHostHelper.prepareNetwork(_publicTrafficInfo.getVirtualSwitchName(), "cloud.public", vmMo.getRunningHost(), vlanId, null, null,
                                _opsTimeout, true, BroadcastDomainType.Vlan, null);
            }
        } else {
            networkInfo =
                    HypervisorHostHelper.prepareNetwork(_publicTrafficInfo.getVirtualSwitchName(), "cloud.public", vmMo.getRunningHost(), vlanId, null, null, null,
                            _opsTimeout, vSwitchType, _portsPerDvPortGroup, null, false, BroadcastDomainType.Vlan, _vsmCredentials);
        }

        int nicIndex = allocPublicNicIndex(vmMo);

        try {
            VirtualDevice[] nicDevices = vmMo.getNicDevices();

            VirtualEthernetCard device = (VirtualEthernetCard)nicDevices[nicIndex];

            if (VirtualSwitchType.StandardVirtualSwitch == vSwitchType) {
                VirtualEthernetCardNetworkBackingInfo nicBacking = new VirtualEthernetCardNetworkBackingInfo();
                nicBacking.setDeviceName(networkInfo.second());
                nicBacking.setNetwork(networkInfo.first());
                device.setBacking(nicBacking);
            } else {
                HostMO hostMo = vmMo.getRunningHost();
                DatacenterMO dataCenterMo = new DatacenterMO(hostMo.getContext(), hostMo.getHyperHostDatacenter());
                device.setBacking(dataCenterMo.getDvPortBackingInfo(networkInfo));
            }

            VirtualMachineConfigSpec vmConfigSpec = new VirtualMachineConfigSpec();

            //VirtualDeviceConfigSpec[] deviceConfigSpecArray = new VirtualDeviceConfigSpec[1];
            VirtualDeviceConfigSpec deviceConfigSpec = new VirtualDeviceConfigSpec();
            deviceConfigSpec.setDevice(device);
            deviceConfigSpec.setOperation(VirtualDeviceConfigSpecOperation.EDIT);

            vmConfigSpec.getDeviceChange().add(deviceConfigSpec);
            if (!vmMo.configureVm(vmConfigSpec)) {
                throw new Exception("Failed to configure devices when plugPublicNic");
            }
        } catch (Exception e) {

            // restore allocation mask in case of exceptions
            String nicMasksStr = vmMo.getCustomFieldValue(CustomFieldConstants.CLOUD_NIC_MASK);
            int nicMasks = Integer.parseInt(nicMasksStr);
            nicMasks &= ~(1 << nicIndex);
            vmMo.setCustomFieldValue(CustomFieldConstants.CLOUD_NIC_MASK, String.valueOf(nicMasks));

            throw e;
        }
    }

    private int allocPublicNicIndex(VirtualMachineMO vmMo) throws Exception {
        String nicMasksStr = vmMo.getCustomFieldValue(CustomFieldConstants.CLOUD_NIC_MASK);
        if (nicMasksStr == null || nicMasksStr.isEmpty()) {
            throw new Exception("Could not find NIC allocation info");
        }

        int nicMasks = Integer.parseInt(nicMasksStr);
        VirtualDevice[] nicDevices = vmMo.getNicDevices();
        for (int i = 3; i < nicDevices.length; i++) {
            if ((nicMasks & (1 << i)) == 0) {
                nicMasks |= (1 << i);
                vmMo.setCustomFieldValue(CustomFieldConstants.CLOUD_NIC_MASK, String.valueOf(nicMasks));
                return i;
            }
        }

        throw new Exception("Could not allocate a free public NIC");
    }

    private ExecutionResult prepareNetworkElementCommand(IpAssocCommand cmd) {
        VmwareContext context = getServiceContext();
        try {
            VmwareHypervisorHost hyperHost = getHyperHost(context);

            IpAddressTO[] ips = cmd.getIpAddresses();
            String routerName = cmd.getAccessDetail(NetworkElementCommand.ROUTER_NAME);
            String controlIp = VmwareResource.getRouterSshControlIp(cmd);

            VirtualMachineMO vmMo = hyperHost.findVmOnHyperHost(routerName);

            // command may sometimes be redirect to a wrong host, we relax
            // the check and will try to find it within cluster
            if (vmMo == null) {
                if (hyperHost instanceof HostMO) {
                    ClusterMO clusterMo = new ClusterMO(hyperHost.getContext(), ((HostMO)hyperHost).getParentMor());
                    vmMo = clusterMo.findVmOnHyperHost(routerName);
                }
            }

            if (vmMo == null) {
                String msg = "Router " + routerName + " no longer exists to execute IPAssoc command";
                s_logger.error(msg);
                throw new Exception(msg);
            }

            for (IpAddressTO ip : ips) {
                /**
                 * TODO support other networks
                 */
                URI broadcastUri = BroadcastDomainType.fromString(ip.getBroadcastUri());
                if (BroadcastDomainType.getSchemeValue(broadcastUri) != BroadcastDomainType.Vlan) {
                    throw new InternalErrorException("Unable to assign a public IP to a VIF on network " + ip.getBroadcastUri());
                }
                String vlanId = BroadcastDomainType.getValue(broadcastUri);

                String publicNeworkName = HypervisorHostHelper.getPublicNetworkNamePrefix(vlanId);
                Pair<Integer, VirtualDevice> publicNicInfo = vmMo.getNicDeviceIndex(publicNeworkName);

                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Find public NIC index, public network name: " + publicNeworkName + ", index: " + publicNicInfo.first());
                }

                boolean addVif = false;
                if (ip.isAdd() && publicNicInfo.first().intValue() == -1) {
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("Plug new NIC to associate" + controlIp + " to " + ip.getPublicIp());
                    }
                    addVif = true;
                }

                if (addVif) {
                    plugPublicNic(vmMo, vlanId, ip.getVifMacAddress());
                    publicNicInfo = vmMo.getNicDeviceIndex(publicNeworkName);
                    if (publicNicInfo.first().intValue() >= 0) {
                        networkUsage(controlIp, "addVif", "eth" + publicNicInfo.first());
                    }
                }

                if (publicNicInfo.first().intValue() < 0) {
                    String msg = "Failed to find DomR VIF to associate/disassociate IP with.";
                    s_logger.error(msg);
                    throw new InternalErrorException(msg);
                }
                ip.setNicDevId(publicNicInfo.first().intValue());
                ip.setNewNic(addVif);
            }
        } catch (Throwable e) {
            s_logger.error("Unexpected exception: " + e.toString() + " will shortcut rest of IPAssoc commands", e);
            return new ExecutionResult(false, e.toString());
        }
        return new ExecutionResult(true, null);
    }

    @Override
    public ExecutionResult executeInVR(String routerIP, String script, String args) {
        return executeInVR(routerIP, script, args, 120);
    }

    @Override
    public ExecutionResult executeInVR(String routerIP, String script, String args, int timeout) {
        Pair<Boolean, String> result;

        //TODO: Password should be masked, cannot output to log directly
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Run command on VR: " + routerIP + ", script: " + script + " with args: " + args);
        }

        try {
            VmwareManager mgr = getServiceContext().getStockObject(VmwareManager.CONTEXT_STOCK_NAME);
            result = SshHelper.sshExecute(routerIP, DefaultDomRSshPort, "root", mgr.getSystemVMKeyFile(), null, "/opt/cloud/bin/" + script + " " + args,
                    60000, 60000, timeout * 1000);
        } catch (Exception e) {
            String msg = "Command failed due to " + VmwareHelper.getExceptionMessage(e);
            s_logger.error(msg);
            result = new Pair<Boolean, String>(false, msg);
        }
        if (s_logger.isDebugEnabled()) {
            s_logger.debug(script + " execution result: " + result.first().toString());
        }
        return new ExecutionResult(result.first(), result.second());
    }

    protected CheckSshAnswer execute(CheckSshCommand cmd) {
        String vmName = cmd.getName();
        String privateIp = cmd.getIp();
        int cmdPort = cmd.getPort();

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Ping command port, " + privateIp + ":" + cmdPort);
        }

        try {
            String result = connect(cmd.getName(), privateIp, cmdPort);
            if (result != null) {
                s_logger.error("Can not ping System vm " + vmName + "due to:" + result);
                return new CheckSshAnswer(cmd, "Can not ping System vm " + vmName + "due to:" + result);
            }
        } catch (Exception e) {
            s_logger.error("Can not ping System vm " + vmName + "due to exception");
            return new CheckSshAnswer(cmd, e);
        }

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Ping command port succeeded for vm " + vmName);
        }

        if (VirtualMachineName.isValidRouterName(vmName)) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Execute network usage setup command on " + vmName);
            }
            networkUsage(privateIp, "create", null);
        }

        return new CheckSshAnswer(cmd);
    }

    private DiskTO[] validateDisks(DiskTO[] disks) {
        List<DiskTO> validatedDisks = new ArrayList<DiskTO>();

        for (DiskTO vol : disks) {
            if (vol.getType() != Volume.Type.ISO) {
                VolumeObjectTO volumeTO = (VolumeObjectTO)vol.getData();
                DataStoreTO primaryStore = volumeTO.getDataStore();
                if (primaryStore.getUuid() != null && !primaryStore.getUuid().isEmpty()) {
                    validatedDisks.add(vol);
                }
            } else if (vol.getType() == Volume.Type.ISO) {
                TemplateObjectTO templateTO = (TemplateObjectTO)vol.getData();
                if (templateTO.getPath() != null && !templateTO.getPath().isEmpty()) {
                    validatedDisks.add(vol);
                }
            } else {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Drop invalid disk option, volumeTO: " + _gson.toJson(vol));
                }
            }
        }

        return validatedDisks.toArray(new DiskTO[0]);
    }

    private static DiskTO getIsoDiskTO(DiskTO[] disks) {
        for (DiskTO vol : disks) {
            if (vol.getType() == Volume.Type.ISO) {
                return vol;
            }
        }
        return null;
    }

    protected ScaleVmAnswer execute(ScaleVmCommand cmd) {

        VmwareContext context = getServiceContext();
        VirtualMachineTO vmSpec = cmd.getVirtualMachine();
        try {
            VmwareHypervisorHost hyperHost = getHyperHost(context);
            VirtualMachineMO vmMo = hyperHost.findVmOnHyperHost(cmd.getVmName());
            VirtualMachineConfigSpec vmConfigSpec = new VirtualMachineConfigSpec();
            int ramMb = getReservedMemoryMb(vmSpec);
            long hotaddIncrementSizeInMb;
            long hotaddMemoryLimitInMb;
            long requestedMaxMemoryInMb = vmSpec.getMaxRam() / (1024 * 1024);

            // Check if VM is really running on hypervisor host
            if (getVmPowerState(vmMo) != PowerState.PowerOn) {
                throw new CloudRuntimeException("Found that the VM " + vmMo.getVmName() + " is not running. Unable to scale-up this VM");
            }

            // Check max hot add limit
            hotaddIncrementSizeInMb = vmMo.getHotAddMemoryIncrementSizeInMb();
            hotaddMemoryLimitInMb = vmMo.getHotAddMemoryLimitInMb();
            if (requestedMaxMemoryInMb > hotaddMemoryLimitInMb) {
                throw new CloudRuntimeException("Memory of VM " + vmMo.getVmName() + " cannot be scaled to " + requestedMaxMemoryInMb + "MB." +
                        " Requested memory limit is beyond the hotadd memory limit for this VM at the moment is " + hotaddMemoryLimitInMb + "MB.");
            }

            // Check increment is multiple of increment size
            long reminder = requestedMaxMemoryInMb % hotaddIncrementSizeInMb;
            if (reminder != 0) {
                requestedMaxMemoryInMb = requestedMaxMemoryInMb + hotaddIncrementSizeInMb - reminder;
            }

            // Check if license supports the feature
            VmwareHelper.isFeatureLicensed(hyperHost, FeatureKeyConstants.HOTPLUG);
            VmwareHelper.setVmScaleUpConfig(vmConfigSpec, vmSpec.getCpus(), vmSpec.getMaxSpeed(), vmSpec.getMinSpeed(), (int)requestedMaxMemoryInMb, ramMb,
                    vmSpec.getLimitCpuUse());

            if (!vmMo.configureVm(vmConfigSpec)) {
                throw new Exception("Unable to execute ScaleVmCommand");
            }
        } catch (Exception e) {
            s_logger.error("Unexpected exception: ", e);
            return new ScaleVmAnswer(cmd, false, "Unable to execute ScaleVmCommand due to " + e.toString());
        }
        return new ScaleVmAnswer(cmd, true, null);
    }

    protected StartAnswer execute(StartCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource StartCommand: " + _gson.toJson(cmd));
        }

        VirtualMachineTO vmSpec = cmd.getVirtualMachine();

        Pair<String, String> names = composeVmNames(vmSpec);
        String vmInternalCSName = names.first();
        String vmNameOnVcenter = names.second();

        // Thus, vmInternalCSName always holds i-x-y, the cloudstack generated internal VM name.
        VmwareContext context = getServiceContext();
        try {
            VmwareManager mgr = context.getStockObject(VmwareManager.CONTEXT_STOCK_NAME);

            VmwareHypervisorHost hyperHost = getHyperHost(context);
            DiskTO[] disks = validateDisks(vmSpec.getDisks());
            assert (disks.length > 0);
            NicTO[] nics = vmSpec.getNics();

            HashMap<String, Pair<ManagedObjectReference, DatastoreMO>> dataStoresDetails = inferDatastoreDetailsFromDiskInfo(hyperHost, context, disks, cmd);
            if ((dataStoresDetails == null) || (dataStoresDetails.isEmpty())) {
                String msg = "Unable to locate datastore details of the volumes to be attached";
                s_logger.error(msg);
                throw new Exception(msg);
            }

            DatastoreMO dsRootVolumeIsOn = getDatastoreThatRootDiskIsOn(dataStoresDetails, disks);
            if (dsRootVolumeIsOn == null) {
                String msg = "Unable to locate datastore details of root volume";
                s_logger.error(msg);
                throw new Exception(msg);
            }

            DatacenterMO dcMo = new DatacenterMO(hyperHost.getContext(), hyperHost.getHyperHostDatacenter());
            VirtualMachineDiskInfoBuilder diskInfoBuilder = null;
            VirtualMachineMO vmMo = hyperHost.findVmOnHyperHost(vmInternalCSName);
            boolean hasSnapshot = false;
            if (vmMo != null) {
                s_logger.info("VM " + vmInternalCSName + " already exists, tear down devices for reconfiguration");
                if (getVmPowerState(vmMo) != PowerState.PowerOff)
                    vmMo.safePowerOff(_shutdownWaitMs);

                // retrieve disk information before we tear down
                diskInfoBuilder = vmMo.getDiskInfoBuilder();
                hasSnapshot = vmMo.hasSnapshot();
                if (!hasSnapshot)
                    vmMo.tearDownDevices(new Class<?>[] {VirtualDisk.class, VirtualEthernetCard.class});
                else
                    vmMo.tearDownDevices(new Class<?>[] {VirtualEthernetCard.class});
                vmMo.ensureScsiDeviceController();
            } else {
                ManagedObjectReference morDc = hyperHost.getHyperHostDatacenter();
                assert (morDc != null);

                vmMo = hyperHost.findVmOnPeerHyperHost(vmInternalCSName);
                if (vmMo != null) {
                    if (s_logger.isInfoEnabled()) {
                        s_logger.info("Found vm " + vmInternalCSName + " at other host, relocate to " + hyperHost.getHyperHostName());
                    }

                    takeVmFromOtherHyperHost(hyperHost, vmInternalCSName);

                    if (getVmPowerState(vmMo) != PowerState.PowerOff)
                        vmMo.safePowerOff(_shutdownWaitMs);

                    diskInfoBuilder = vmMo.getDiskInfoBuilder();
                    hasSnapshot = vmMo.hasSnapshot();
                    if (!hasSnapshot)
                        vmMo.tearDownDevices(new Class<?>[] {VirtualDisk.class, VirtualEthernetCard.class});
                    else
                        vmMo.tearDownDevices(new Class<?>[] {VirtualEthernetCard.class});
                    vmMo.ensureScsiDeviceController();
                } else {
                    Pair<ManagedObjectReference, DatastoreMO> rootDiskDataStoreDetails = null;
                    for (DiskTO vol : disks) {
                        if (vol.getType() == Volume.Type.ROOT) {
                            Map<String, String> details = vol.getDetails();
                            boolean managed = false;

                            if (details != null) {
                                managed = Boolean.parseBoolean(details.get(DiskTO.MANAGED));
                            }

                            if (managed) {
                                String datastoreName = VmwareResource.getDatastoreName(details.get(DiskTO.IQN));

                                rootDiskDataStoreDetails = dataStoresDetails.get(datastoreName);
                            }
                            else {
                                DataStoreTO primaryStore = vol.getData().getDataStore();

                                rootDiskDataStoreDetails = dataStoresDetails.get(primaryStore.getUuid());
                            }
                        }
                    }

                    assert (vmSpec.getMinSpeed() != null) && (rootDiskDataStoreDetails != null);

                    if (rootDiskDataStoreDetails.second().folderExists(String.format("[%s]", rootDiskDataStoreDetails.second().getName()), vmNameOnVcenter)) {
                        registerVm(vmNameOnVcenter, dsRootVolumeIsOn);
                        vmMo = hyperHost.findVmOnHyperHost(vmInternalCSName);
                        tearDownVm(vmMo);
                    }else if (!hyperHost.createBlankVm(vmNameOnVcenter, vmInternalCSName, vmSpec.getCpus(), vmSpec.getMaxSpeed().intValue(),
                            getReservedCpuMHZ(vmSpec), vmSpec.getLimitCpuUse(), (int)(vmSpec.getMaxRam() / (1024 * 1024)), getReservedMemoryMb(vmSpec),
                            translateGuestOsIdentifier(vmSpec.getArch(), vmSpec.getOs(), vmSpec.getPlatformEmulator()).value(), rootDiskDataStoreDetails.first(), false)) {
                        throw new Exception("Failed to create VM. vmName: " + vmInternalCSName);
                    }
                }

                vmMo = hyperHost.findVmOnHyperHost(vmInternalCSName);
                if (vmMo == null) {
                    throw new Exception("Failed to find the newly create or relocated VM. vmName: " + vmInternalCSName);
                }
            }

            int totalChangeDevices = disks.length + nics.length;

            DiskTO volIso = null;
            if (vmSpec.getType() != VirtualMachine.Type.User) {
                // system VM needs a patch ISO
                totalChangeDevices++;
            } else {
                volIso = getIsoDiskTO(disks);
                if (volIso == null)
                    totalChangeDevices++;
            }

            VirtualMachineConfigSpec vmConfigSpec = new VirtualMachineConfigSpec();
            String guestOsId = translateGuestOsIdentifier(vmSpec.getArch(), vmSpec.getOs(), vmSpec.getPlatformEmulator()).value();

            VmwareHelper.setBasicVmConfig(vmConfigSpec, vmSpec.getCpus(), vmSpec.getMaxSpeed(),
                    getReservedCpuMHZ(vmSpec), (int)(vmSpec.getMaxRam() / (1024 * 1024)), getReservedMemoryMb(vmSpec),
                    guestOsId, vmSpec.getLimitCpuUse());

            // Check for multi-cores per socket settings
            int numCoresPerSocket = 1;
            String coresPerSocket = vmSpec.getDetails().get("cpu.corespersocket");
            if (coresPerSocket != null) {
                String apiVersion = HypervisorHostHelper.getVcenterApiVersion(vmMo.getContext());
                // Property 'numCoresPerSocket' is supported since vSphere API 5.0
                if (apiVersion.compareTo("5.0") >= 0) {
                    numCoresPerSocket = NumbersUtil.parseInt(coresPerSocket, 1);
                    vmConfigSpec.setNumCoresPerSocket(numCoresPerSocket);
                }
            }

            // Check for hotadd settings
            vmConfigSpec.setMemoryHotAddEnabled(vmMo.isMemoryHotAddSupported(guestOsId));

            String hostApiVersion = ((HostMO)hyperHost).getHostAboutInfo().getApiVersion();
            if (numCoresPerSocket > 1 && hostApiVersion.compareTo("5.0") < 0) {
                s_logger.warn("Dynamic scaling of CPU is not supported for Virtual Machines with multi-core vCPUs in case of ESXi hosts 4.1 and prior. Hence CpuHotAdd will not be"
                        + " enabled for Virtual Machine: " + vmInternalCSName);
                vmConfigSpec.setCpuHotAddEnabled(false);
            } else {
                vmConfigSpec.setCpuHotAddEnabled(vmMo.isCpuHotAddSupported(guestOsId));
            }

            configNestedHVSupport(vmMo, vmSpec, vmConfigSpec);

            VirtualDeviceConfigSpec[] deviceConfigSpecArray = new VirtualDeviceConfigSpec[totalChangeDevices];
            int i = 0;
            int ideUnitNumber = 0;
            int scsiUnitNumber = 0;
            int nicUnitNumber = 0;
            int ideControllerKey = vmMo.getIDEDeviceControllerKey();
            int scsiControllerKey = vmMo.getScsiDeviceControllerKey();
            int controllerKey;

            //
            // Setup ISO device
            //

            // prepare systemvm patch ISO
            if (vmSpec.getType() != VirtualMachine.Type.User) {
                // attach ISO (for patching of system VM)
                String secStoreUrl = mgr.getSecondaryStorageStoreUrl(Long.parseLong(_dcId));
                if (secStoreUrl == null) {
                    String msg = "secondary storage for dc " + _dcId + " is not ready yet?";
                    throw new Exception(msg);
                }
                mgr.prepareSecondaryStorageStore(secStoreUrl);

                ManagedObjectReference morSecDs = prepareSecondaryDatastoreOnHost(secStoreUrl);
                if (morSecDs == null) {
                    String msg = "Failed to prepare secondary storage on host, secondary store url: " + secStoreUrl;
                    throw new Exception(msg);
                }
                DatastoreMO secDsMo = new DatastoreMO(hyperHost.getContext(), morSecDs);

                deviceConfigSpecArray[i] = new VirtualDeviceConfigSpec();
                Pair<VirtualDevice, Boolean> isoInfo =
                        VmwareHelper.prepareIsoDevice(vmMo, String.format("[%s] systemvm/%s", secDsMo.getName(), mgr.getSystemVMIsoFileNameOnDatastore()), secDsMo.getMor(),
                                true, true, ideUnitNumber++, i + 1);
                deviceConfigSpecArray[i].setDevice(isoInfo.first());
                if (isoInfo.second()) {
                    if (s_logger.isDebugEnabled())
                        s_logger.debug("Prepare ISO volume at new device " + _gson.toJson(isoInfo.first()));
                    deviceConfigSpecArray[i].setOperation(VirtualDeviceConfigSpecOperation.ADD);
                } else {
                    if (s_logger.isDebugEnabled())
                        s_logger.debug("Prepare ISO volume at existing device " + _gson.toJson(isoInfo.first()));
                    deviceConfigSpecArray[i].setOperation(VirtualDeviceConfigSpecOperation.EDIT);
                }
            } else {
                // Note: we will always plug a CDROM device
                if (volIso != null) {
                    TemplateObjectTO iso = (TemplateObjectTO)volIso.getData();

                    if (iso.getPath() != null && !iso.getPath().isEmpty()) {
                        DataStoreTO imageStore = iso.getDataStore();
                        if (!(imageStore instanceof NfsTO)) {
                            s_logger.debug("unsupported protocol");
                            throw new Exception("unsupported protocol");
                        }
                        NfsTO nfsImageStore = (NfsTO)imageStore;
                        String isoPath = nfsImageStore.getUrl() + File.separator + iso.getPath();
                        Pair<String, ManagedObjectReference> isoDatastoreInfo = getIsoDatastoreInfo(hyperHost, isoPath);
                        assert (isoDatastoreInfo != null);
                        assert (isoDatastoreInfo.second() != null);

                        deviceConfigSpecArray[i] = new VirtualDeviceConfigSpec();
                        Pair<VirtualDevice, Boolean> isoInfo =
                                VmwareHelper.prepareIsoDevice(vmMo, isoDatastoreInfo.first(), isoDatastoreInfo.second(), true, true, ideUnitNumber++, i + 1);
                        deviceConfigSpecArray[i].setDevice(isoInfo.first());
                        if (isoInfo.second()) {
                            if (s_logger.isDebugEnabled())
                                s_logger.debug("Prepare ISO volume at new device " + _gson.toJson(isoInfo.first()));
                            deviceConfigSpecArray[i].setOperation(VirtualDeviceConfigSpecOperation.ADD);
                        } else {
                            if (s_logger.isDebugEnabled())
                                s_logger.debug("Prepare ISO volume at existing device " + _gson.toJson(isoInfo.first()));
                            deviceConfigSpecArray[i].setOperation(VirtualDeviceConfigSpecOperation.EDIT);
                        }
                    }
                } else {
                    deviceConfigSpecArray[i] = new VirtualDeviceConfigSpec();
                    Pair<VirtualDevice, Boolean> isoInfo = VmwareHelper.prepareIsoDevice(vmMo, null, null, true, true, ideUnitNumber++, i + 1);
                    deviceConfigSpecArray[i].setDevice(isoInfo.first());
                    if (isoInfo.second()) {
                        if (s_logger.isDebugEnabled())
                            s_logger.debug("Prepare ISO volume at existing device " + _gson.toJson(isoInfo.first()));

                        deviceConfigSpecArray[i].setOperation(VirtualDeviceConfigSpecOperation.ADD);
                    } else {
                        if (s_logger.isDebugEnabled())
                            s_logger.debug("Prepare ISO volume at existing device " + _gson.toJson(isoInfo.first()));

                        deviceConfigSpecArray[i].setOperation(VirtualDeviceConfigSpecOperation.EDIT);
                    }
                }
            }

            i++;

            //
            // Setup ROOT/DATA disk devices
            //
            DiskTO[] sortedDisks = sortVolumesByDeviceId(disks);
            for (DiskTO vol : sortedDisks) {
                if (vol.getType() == Volume.Type.ISO)
                    continue;

                VirtualMachineDiskInfo matchingExistingDisk = getMatchingExistingDisk(diskInfoBuilder, vol, hyperHost, context);
                controllerKey = getDiskController(matchingExistingDisk, vol, vmSpec, ideControllerKey, scsiControllerKey);

                if (!hasSnapshot) {
                    deviceConfigSpecArray[i] = new VirtualDeviceConfigSpec();

                    VolumeObjectTO volumeTO = (VolumeObjectTO)vol.getData();
                    DataStoreTO primaryStore = volumeTO.getDataStore();
                    Map<String, String> details = vol.getDetails();
                    boolean managed = false;
                    String iScsiName = null;

                    if (details != null) {
                        managed = Boolean.parseBoolean(details.get(DiskTO.MANAGED));
                        iScsiName = details.get(DiskTO.IQN);
                    }

                    // if the storage is managed, iScsiName should not be null
                    String datastoreName = managed ? VmwareResource.getDatastoreName(iScsiName) : primaryStore.getUuid();
                    Pair<ManagedObjectReference, DatastoreMO> volumeDsDetails = dataStoresDetails.get(datastoreName);

                    assert (volumeDsDetails != null);

                    String[] diskChain = syncDiskChain(dcMo, vmMo, vmSpec,
                            vol, matchingExistingDisk,
                            dataStoresDetails);
                    if(controllerKey == scsiControllerKey && VmwareHelper.isReservedScsiDeviceNumber(scsiUnitNumber))
                        scsiUnitNumber++;
                    VirtualDevice device = VmwareHelper.prepareDiskDevice(vmMo, null, controllerKey,
                            diskChain,
                            volumeDsDetails.first(),
                            (controllerKey == ideControllerKey) ? ideUnitNumber++ : scsiUnitNumber++, i + 1);

                    deviceConfigSpecArray[i].setDevice(device);
                    deviceConfigSpecArray[i].setOperation(VirtualDeviceConfigSpecOperation.ADD);

                    if(s_logger.isDebugEnabled())
                        s_logger.debug("Prepare volume at new device " + _gson.toJson(device));

                    i++;
                } else {
                    if (controllerKey == scsiControllerKey && VmwareHelper.isReservedScsiDeviceNumber(scsiUnitNumber))
                        scsiUnitNumber++;
                    if (controllerKey == ideControllerKey)
                        ideUnitNumber++;
                    else
                        scsiUnitNumber++;
                }
            }

            //
            // Setup NIC devices
            //
            VirtualDevice nic;
            int nicMask = 0;
            int nicCount = 0;
            VirtualEthernetCardType nicDeviceType = VirtualEthernetCardType.valueOf(vmSpec.getDetails().get(VmDetailConstants.NIC_ADAPTER));
            if (s_logger.isDebugEnabled())
                s_logger.debug("VM " + vmInternalCSName + " will be started with NIC device type: " + nicDeviceType);

            for (NicTO nicTo : sortNicsByDeviceId(nics)) {
                s_logger.info("Prepare NIC device based on NicTO: " + _gson.toJson(nicTo));

                boolean configureVServiceInNexus = (nicTo.getType() == TrafficType.Guest) && (vmSpec.getDetails().containsKey("ConfigureVServiceInNexus"));
                VirtualMachine.Type vmType = cmd.getVirtualMachine().getType();
                Pair<ManagedObjectReference, String> networkInfo = prepareNetworkFromNicInfo(vmMo.getRunningHost(), nicTo, configureVServiceInNexus, vmType);
                if (VmwareHelper.isDvPortGroup(networkInfo.first())) {
                    String dvSwitchUuid;
                    ManagedObjectReference dcMor = hyperHost.getHyperHostDatacenter();
                    DatacenterMO dataCenterMo = new DatacenterMO(context, dcMor);
                    ManagedObjectReference dvsMor = dataCenterMo.getDvSwitchMor(networkInfo.first());
                    dvSwitchUuid = dataCenterMo.getDvSwitchUuid(dvsMor);
                    s_logger.info("Preparing NIC device on dvSwitch : " + dvSwitchUuid);
                    nic =
                            VmwareHelper.prepareDvNicDevice(vmMo, networkInfo.first(), nicDeviceType, networkInfo.second(), dvSwitchUuid, nicTo.getMac(), nicUnitNumber++,
                                    i + 1, true, true);
                } else {
                    s_logger.info("Preparing NIC device on network " + networkInfo.second());
                    nic =
                            VmwareHelper.prepareNicDevice(vmMo, networkInfo.first(), nicDeviceType, networkInfo.second(), nicTo.getMac(), nicUnitNumber++, i + 1, true, true);
                }

                deviceConfigSpecArray[i] = new VirtualDeviceConfigSpec();
                deviceConfigSpecArray[i].setDevice(nic);
                deviceConfigSpecArray[i].setOperation(VirtualDeviceConfigSpecOperation.ADD);

                if (s_logger.isDebugEnabled())
                    s_logger.debug("Prepare NIC at new device " + _gson.toJson(deviceConfigSpecArray[i]));

                // this is really a hacking for DomR, upon DomR startup, we will reset all the NIC allocation after eth3
                if (nicCount < 3)
                    nicMask |= (1 << nicCount);

                i++;
                nicCount++;
            }

            for (int j = 0; j < i; j++)
                vmConfigSpec.getDeviceChange().add(deviceConfigSpecArray[j]);

            //
            // Setup VM options
            //

            // pass boot arguments through machine.id & perform customized options to VMX
            ArrayList<OptionValue> extraOptions = new ArrayList<OptionValue>();
            configBasicExtraOption(extraOptions, vmSpec);
            configNvpExtraOption(extraOptions, vmSpec);
            configCustomExtraOption(extraOptions, vmSpec);

            // config VNC
            String keyboardLayout = null;
            if (vmSpec.getDetails() != null)
                keyboardLayout = vmSpec.getDetails().get(VmDetailConstants.KEYBOARD);
            vmConfigSpec.getExtraConfig().addAll(
                    Arrays.asList(configureVnc(extraOptions.toArray(new OptionValue[0]), hyperHost, vmInternalCSName, vmSpec.getVncPassword(), keyboardLayout)));

            //
            // Configure VM
            //
            if (!vmMo.configureVm(vmConfigSpec)) {
                throw new Exception("Failed to configure VM before start. vmName: " + vmInternalCSName);
            }

            //
            // Post Configuration
            //

            vmMo.setCustomFieldValue(CustomFieldConstants.CLOUD_NIC_MASK, String.valueOf(nicMask));
            postNvpConfigBeforeStart(vmMo, vmSpec);

            Map<String, String> iqnToPath = new HashMap<String, String>();

            postDiskConfigBeforeStart(vmMo, vmSpec, sortedDisks, ideControllerKey, scsiControllerKey, iqnToPath, hyperHost, context);

            //
            // Power-on VM
            //
            if (!vmMo.powerOn()) {
                throw new Exception("Failed to start VM. vmName: " + vmInternalCSName + " with hostname " + vmNameOnVcenter);
            }

            StartAnswer startAnswer = new StartAnswer(cmd);

            startAnswer.setIqnToPath(iqnToPath);

            return startAnswer;
        } catch (Throwable e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }

            String msg = "StartCommand failed due to " + VmwareHelper.getExceptionMessage(e);
            s_logger.warn(msg, e);
            return new StartAnswer(cmd, msg);
        } finally {
        }
    }

    private void tearDownVm(VirtualMachineMO vmMo) throws Exception{

        if(vmMo == null) return;

        boolean hasSnapshot = false;
        hasSnapshot = vmMo.hasSnapshot();
        if (!hasSnapshot)
            vmMo.tearDownDevices(new Class<?>[] {VirtualDisk.class, VirtualEthernetCard.class});
        else
            vmMo.tearDownDevices(new Class<?>[] {VirtualEthernetCard.class});
        vmMo.ensureScsiDeviceController();
    }

    int getReservedMemoryMb(VirtualMachineTO vmSpec) {
         if (vmSpec.getDetails().get(VMwareGuru.VmwareReserveMemory.key()).equalsIgnoreCase("true")) {
             return  (int) (vmSpec.getMinRam() / (1024 * 1024));
         }
         return 0;
    }

    int getReservedCpuMHZ(VirtualMachineTO vmSpec) {
         if (vmSpec.getDetails().get(VMwareGuru.VmwareReserveCpu.key()).equalsIgnoreCase("true")) {
             return vmSpec.getMinSpeed();
         }
         return 0;
    }

    // return the finalized disk chain for startup, from top to bottom
    private String[] syncDiskChain(DatacenterMO dcMo, VirtualMachineMO vmMo, VirtualMachineTO vmSpec, DiskTO vol, VirtualMachineDiskInfo diskInfo,
            HashMap<String, Pair<ManagedObjectReference, DatastoreMO>> dataStoresDetails) throws Exception {

        VolumeObjectTO volumeTO = (VolumeObjectTO)vol.getData();
        DataStoreTO primaryStore = volumeTO.getDataStore();
        Map<String, String> details = vol.getDetails();
        boolean isManaged = false;
        String iScsiName = null;

        if (details != null) {
            isManaged = Boolean.parseBoolean(details.get(DiskTO.MANAGED));
            iScsiName = details.get(DiskTO.IQN);
        }

        // if the storage is managed, iScsiName should not be null
        String datastoreName = isManaged ? VmwareResource.getDatastoreName(iScsiName) : primaryStore.getUuid();
        Pair<ManagedObjectReference, DatastoreMO> volumeDsDetails = dataStoresDetails.get(datastoreName);

        if (volumeDsDetails == null)
        {
            throw new Exception("Primary datastore " + primaryStore.getUuid() + " is not mounted on host.");
        }

        DatastoreMO dsMo = volumeDsDetails.second();

        // we will honor vCenter's meta if it exists
        if (diskInfo != null) {
            // to deal with run-time upgrade to maintain the new datastore folder structure
            String disks[] = diskInfo.getDiskChain();
            for (int i = 0; i < disks.length; i++) {
                DatastoreFile file = new DatastoreFile(disks[i]);
                if (!isManaged && file.getDir() != null && file.getDir().isEmpty()) {
                    s_logger.info("Perform run-time datastore folder upgrade. sync " + disks[i] + " to VM folder");
                    disks[i] = VmwareStorageLayoutHelper.syncVolumeToVmDefaultFolder(dcMo, vmMo.getName(), dsMo, file.getFileBaseName());
                }
            }
            return disks;
        }

        final String datastoreDiskPath;

        if (isManaged) {
            if (volumeTO.getVolumeType() == Volume.Type.ROOT) {
                datastoreDiskPath = VmwareStorageLayoutHelper.syncVolumeToVmDefaultFolder(dcMo, vmMo.getName(), dsMo, volumeTO.getName());
            }
            else {
                datastoreDiskPath = dsMo.getDatastorePath(dsMo.getName() + ".vmdk");
            }
        } else {
            datastoreDiskPath = VmwareStorageLayoutHelper.syncVolumeToVmDefaultFolder(dcMo, vmMo.getName(), dsMo, volumeTO.getPath());
        }

        if (!dsMo.fileExists(datastoreDiskPath)) {
            s_logger.warn("Volume " + volumeTO.getId() + " does not seem to exist on datastore, out of sync? path: " + datastoreDiskPath);
        }

        return new String[] {datastoreDiskPath};
    }

    // Pair<internal CS name, vCenter display name>
    private Pair<String, String> composeVmNames(VirtualMachineTO vmSpec) {
        String vmInternalCSName = vmSpec.getName();
        String vmNameOnVcenter = vmSpec.getName();
        if (vmSpec.getType() == VirtualMachine.Type.User && _instanceNameFlag && vmSpec.getHostName() != null) {
            String[] tokens = vmInternalCSName.split("-");
            assert (tokens.length >= 3); // vmInternalCSName has format i-x-y-<instance.name>
            vmNameOnVcenter = String.format("%s-%s-%s-%s", tokens[0], tokens[1], tokens[2], vmSpec.getHostName());
        }
        return new Pair<String, String>(vmInternalCSName, vmNameOnVcenter);
    }

    private static void configNestedHVSupport(VirtualMachineMO vmMo, VirtualMachineTO vmSpec, VirtualMachineConfigSpec vmConfigSpec) throws Exception {

        VmwareContext context = vmMo.getContext();
        if ("true".equals(vmSpec.getDetails().get(VmDetailConstants.NESTED_VIRTUALIZATION_FLAG))) {
            if (s_logger.isDebugEnabled())
                s_logger.debug("Nested Virtualization enabled in configuration, checking hypervisor capability");

            ManagedObjectReference hostMor = vmMo.getRunningHost().getMor();
            ManagedObjectReference computeMor = context.getVimClient().getMoRefProp(hostMor, "parent");
            ManagedObjectReference environmentBrowser = context.getVimClient().getMoRefProp(computeMor, "environmentBrowser");
            HostCapability hostCapability = context.getService().queryTargetCapabilities(environmentBrowser, hostMor);
            Boolean nestedHvSupported = hostCapability.isNestedHVSupported();
            if (nestedHvSupported == null) {
                // nestedHvEnabled property is supported only since VMware 5.1. It's not defined for earlier versions.
                s_logger.warn("Hypervisor doesn't support nested virtualization, unable to set config for VM " + vmSpec.getName());
            } else if (nestedHvSupported.booleanValue()) {
                s_logger.debug("Hypervisor supports nested virtualization, enabling for VM " + vmSpec.getName());
                vmConfigSpec.setNestedHVEnabled(true);
            } else {
                s_logger.warn("Hypervisor doesn't support nested virtualization, unable to set config for VM " + vmSpec.getName());
                vmConfigSpec.setNestedHVEnabled(false);
            }
        }
    }

    private static void configBasicExtraOption(List<OptionValue> extraOptions, VirtualMachineTO vmSpec) {
        OptionValue newVal = new OptionValue();
        newVal.setKey("machine.id");
        newVal.setValue(vmSpec.getBootArgs());
        extraOptions.add(newVal);

        newVal = new OptionValue();
        newVal.setKey("devices.hotplug");
        newVal.setValue("true");
        extraOptions.add(newVal);
    }

    private static void configNvpExtraOption(List<OptionValue> extraOptions, VirtualMachineTO vmSpec) {
        /**
         * Extra Config : nvp.vm-uuid = uuid
         *  - Required for Nicira NVP integration
         */
        OptionValue newVal = new OptionValue();
        newVal.setKey("nvp.vm-uuid");
        newVal.setValue(vmSpec.getUuid());
        extraOptions.add(newVal);

        /**
         * Extra Config : nvp.iface-id.<num> = uuid
         *  - Required for Nicira NVP integration
         */
        int nicNum = 0;
        for (NicTO nicTo : sortNicsByDeviceId(vmSpec.getNics())) {
            if (nicTo.getUuid() != null) {
                newVal = new OptionValue();
                newVal.setKey("nvp.iface-id." + nicNum);
                newVal.setValue(nicTo.getUuid());
                extraOptions.add(newVal);
                setNuageVspVrIpInExtraConfig(extraOptions, nicTo);
            }
            nicNum++;
        }
    }

    private static void setNuageVspVrIpInExtraConfig(List<OptionValue> extraOptions, NicTO nicTo) {
        URI broadcastUri = nicTo.getBroadcastUri();
        if (broadcastUri != null && broadcastUri.getScheme().equalsIgnoreCase(Networks.BroadcastDomainType.Vsp.scheme())) {
            String path = broadcastUri.getPath();
            OptionValue newVal = new OptionValue();
            newVal.setKey("vsp.vr-ip." + nicTo.getMac());
            newVal.setValue(path.substring(1));
            extraOptions.add(newVal);
        }
    }

    private static void configCustomExtraOption(List<OptionValue> extraOptions, VirtualMachineTO vmSpec) {
        // we no longer to validation anymore
        for (Map.Entry<String, String> entry : vmSpec.getDetails().entrySet()) {
            OptionValue newVal = new OptionValue();
            newVal.setKey(entry.getKey());
            newVal.setValue(entry.getValue());
            extraOptions.add(newVal);
        }
    }

    private static void postNvpConfigBeforeStart(VirtualMachineMO vmMo, VirtualMachineTO vmSpec) throws Exception {
        /**
         * We need to configure the port on the DV switch after the host is
         * connected. So make this happen between the configure and start of
         * the VM
         */
        int nicIndex = 0;
        for (NicTO nicTo : sortNicsByDeviceId(vmSpec.getNics())) {
            if (nicTo.getBroadcastType() == BroadcastDomainType.Lswitch) {
                // We need to create a port with a unique vlan and pass the key to the nic device
                s_logger.trace("Nic " + nicTo.toString() + " is connected to an NVP logicalswitch");
                VirtualDevice nicVirtualDevice = vmMo.getNicDeviceByIndex(nicIndex);
                if (nicVirtualDevice == null) {
                    throw new Exception("Failed to find a VirtualDevice for nic " + nicIndex); //FIXME Generic exceptions are bad
                }
                VirtualDeviceBackingInfo backing = nicVirtualDevice.getBacking();
                if (backing instanceof VirtualEthernetCardDistributedVirtualPortBackingInfo) {
                    // This NIC is connected to a Distributed Virtual Switch
                    VirtualEthernetCardDistributedVirtualPortBackingInfo portInfo = (VirtualEthernetCardDistributedVirtualPortBackingInfo)backing;
                    DistributedVirtualSwitchPortConnection port = portInfo.getPort();
                    String portKey = port.getPortKey();
                    String portGroupKey = port.getPortgroupKey();
                    String dvSwitchUuid = port.getSwitchUuid();

                    s_logger.debug("NIC " + nicTo.toString() + " is connected to dvSwitch " + dvSwitchUuid + " pg " + portGroupKey + " port " + portKey);

                    ManagedObjectReference dvSwitchManager = vmMo.getContext().getVimClient().getServiceContent().getDvSwitchManager();
                    ManagedObjectReference dvSwitch = vmMo.getContext().getVimClient().getService().queryDvsByUuid(dvSwitchManager, dvSwitchUuid);

                    // Get all ports
                    DistributedVirtualSwitchPortCriteria criteria = new DistributedVirtualSwitchPortCriteria();
                    criteria.setInside(true);
                    criteria.getPortgroupKey().add(portGroupKey);
                    List<DistributedVirtualPort> dvPorts = vmMo.getContext().getVimClient().getService().fetchDVPorts(dvSwitch, criteria);

                    DistributedVirtualPort vmDvPort = null;
                    List<Integer> usedVlans = new ArrayList<Integer>();
                    for (DistributedVirtualPort dvPort : dvPorts) {
                        // Find the port for this NIC by portkey
                        if (portKey.equals(dvPort.getKey())) {
                            vmDvPort = dvPort;
                        }
                        VMwareDVSPortSetting settings = (VMwareDVSPortSetting)dvPort.getConfig().getSetting();
                        VmwareDistributedVirtualSwitchVlanIdSpec vlanId = (VmwareDistributedVirtualSwitchVlanIdSpec)settings.getVlan();
                        s_logger.trace("Found port " + dvPort.getKey() + " with vlan " + vlanId.getVlanId());
                        if (vlanId.getVlanId() > 0 && vlanId.getVlanId() < 4095) {
                            usedVlans.add(vlanId.getVlanId());
                        }
                    }

                    if (vmDvPort == null) {
                        throw new Exception("Empty port list from dvSwitch for nic " + nicTo.toString());
                    }

                    DVPortConfigInfo dvPortConfigInfo = vmDvPort.getConfig();
                    VMwareDVSPortSetting settings = (VMwareDVSPortSetting)dvPortConfigInfo.getSetting();

                    VmwareDistributedVirtualSwitchVlanIdSpec vlanId = (VmwareDistributedVirtualSwitchVlanIdSpec)settings.getVlan();
                    BoolPolicy blocked = settings.getBlocked();
                    if (blocked.isValue() == Boolean.TRUE) {
                        s_logger.trace("Port is blocked, set a vlanid and unblock");
                        DVPortConfigSpec dvPortConfigSpec = new DVPortConfigSpec();
                        VMwareDVSPortSetting edittedSettings = new VMwareDVSPortSetting();
                        // Unblock
                        blocked.setValue(Boolean.FALSE);
                        blocked.setInherited(Boolean.FALSE);
                        edittedSettings.setBlocked(blocked);
                        // Set vlan
                        int i;
                        for (i = 1; i < 4095; i++) {
                            if (!usedVlans.contains(i))
                                break;
                        }
                        vlanId.setVlanId(i); // FIXME should be a determined
                        // based on usage
                        vlanId.setInherited(false);
                        edittedSettings.setVlan(vlanId);

                        dvPortConfigSpec.setSetting(edittedSettings);
                        dvPortConfigSpec.setOperation("edit");
                        dvPortConfigSpec.setKey(portKey);
                        List<DVPortConfigSpec> dvPortConfigSpecs = new ArrayList<DVPortConfigSpec>();
                        dvPortConfigSpecs.add(dvPortConfigSpec);
                        ManagedObjectReference task = vmMo.getContext().getVimClient().getService().reconfigureDVPortTask(dvSwitch, dvPortConfigSpecs);
                        if (!vmMo.getContext().getVimClient().waitForTask(task)) {
                            throw new Exception("Failed to configure the dvSwitch port for nic " + nicTo.toString());
                        }
                        s_logger.debug("NIC " + nicTo.toString() + " connected to vlan " + i);
                    } else {
                        s_logger.trace("Port already configured and set to vlan " + vlanId.getVlanId());
                    }
                } else if (backing instanceof VirtualEthernetCardNetworkBackingInfo) {
                    // This NIC is connected to a Virtual Switch
                    // Nothing to do
                } else {
                    s_logger.error("nic device backing is of type " + backing.getClass().getName());
                    throw new Exception("Incompatible backing for a VirtualDevice for nic " + nicIndex); //FIXME Generic exceptions are bad
                }
            }
            nicIndex++;
        }
    }

    private VirtualMachineDiskInfo getMatchingExistingDisk(VirtualMachineDiskInfoBuilder diskInfoBuilder, DiskTO vol,
            VmwareHypervisorHost hyperHost, VmwareContext context) throws Exception {
        if (diskInfoBuilder != null) {
            VolumeObjectTO volume = (VolumeObjectTO)vol.getData();

            ManagedObjectReference morDs = HypervisorHostHelper.findDatastoreWithBackwardsCompatibility(hyperHost, volume.getDataStore().getUuid());
            DatastoreMO dsMo = new DatastoreMO(context, morDs);
            String dsName = dsMo.getName();

            Map<String, String> details = vol.getDetails();
            boolean isManaged = details != null && Boolean.parseBoolean(details.get(DiskTO.MANAGED));

            VirtualMachineDiskInfo diskInfo =
                    diskInfoBuilder.getDiskInfoByBackingFileBaseName(isManaged ? new DatastoreFile(volume.getPath()).getFileBaseName() : volume.getPath(), dsName);
            if (diskInfo != null) {
                s_logger.info("Found existing disk info from volume path: " + volume.getPath());
                return diskInfo;
            } else {
                String chainInfo = volume.getChainInfo();
                if (chainInfo != null) {
                    VirtualMachineDiskInfo infoInChain = _gson.fromJson(chainInfo, VirtualMachineDiskInfo.class);
                    if (infoInChain != null) {
                        String[] disks = infoInChain.getDiskChain();
                        if (disks.length > 0) {
                            for (String diskPath : disks) {
                                DatastoreFile file = new DatastoreFile(diskPath);
                                diskInfo = diskInfoBuilder.getDiskInfoByBackingFileBaseName(file.getFileBaseName(), dsName);
                                if (diskInfo != null) {
                                    s_logger.info("Found existing disk from chain info: " + diskPath);
                                    return diskInfo;
                                }
                            }
                        }

                        if (diskInfo == null) {
                            diskInfo = diskInfoBuilder.getDiskInfoByDeviceBusName(infoInChain.getDiskDeviceBusName());
                            if (diskInfo != null) {
                                s_logger.info("Found existing disk from from chain device bus information: " + infoInChain.getDiskDeviceBusName());
                                return diskInfo;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private int getDiskController(VirtualMachineDiskInfo matchingExistingDisk, DiskTO vol, VirtualMachineTO vmSpec, int ideControllerKey, int scsiControllerKey) {

        int controllerKey;
        if (matchingExistingDisk != null) {
            s_logger.info("Chose disk controller based on existing information: " + matchingExistingDisk.getDiskDeviceBusName());
            if (matchingExistingDisk.getDiskDeviceBusName().startsWith("ide"))
                return ideControllerKey;
            else
                return scsiControllerKey;
        }

        if (vol.getType() == Volume.Type.ROOT) {
            Map<String, String> vmDetails = vmSpec.getDetails();
            if (vmDetails != null && vmDetails.get(VmDetailConstants.ROOK_DISK_CONTROLLER) != null) {
                if (vmDetails.get(VmDetailConstants.ROOK_DISK_CONTROLLER).equalsIgnoreCase("scsi")) {
                    s_logger.info("Chose disk controller for vol " + vol.getType() + " -> scsi, based on root disk controller settings: " +
                            vmDetails.get(VmDetailConstants.ROOK_DISK_CONTROLLER));
                    controllerKey = scsiControllerKey;
                } else {
                    s_logger.info("Chose disk controller for vol " + vol.getType() + " -> ide, based on root disk controller settings: " +
                            vmDetails.get(VmDetailConstants.ROOK_DISK_CONTROLLER));
                    controllerKey = ideControllerKey;
                }
            } else {
                s_logger.info("Chose disk controller for vol " + vol.getType() + " -> scsi. due to null root disk controller setting");
                controllerKey = scsiControllerKey;
            }

        } else {
            // DATA volume always use SCSI device
            s_logger.info("Chose disk controller for vol " + vol.getType() + " -> scsi");
            controllerKey = scsiControllerKey;
        }

        return controllerKey;
    }

    private void postDiskConfigBeforeStart(VirtualMachineMO vmMo, VirtualMachineTO vmSpec, DiskTO[] sortedDisks, int ideControllerKey,
            int scsiControllerKey, Map<String, String> iqnToPath, VmwareHypervisorHost hyperHost, VmwareContext context) throws Exception {
        VirtualMachineDiskInfoBuilder diskInfoBuilder = vmMo.getDiskInfoBuilder();

        for (DiskTO vol : sortedDisks) {
            if (vol.getType() == Volume.Type.ISO)
                continue;

            VolumeObjectTO volumeTO = (VolumeObjectTO)vol.getData();

            VirtualMachineDiskInfo diskInfo = getMatchingExistingDisk(diskInfoBuilder, vol, hyperHost, context);
            assert (diskInfo != null);

            String[] diskChain = diskInfo.getDiskChain();
            assert (diskChain.length > 0);

            Map<String, String> details = vol.getDetails();
            boolean managed = false;

            if (details != null) {
                managed = Boolean.parseBoolean(details.get(DiskTO.MANAGED));
            }

            DatastoreFile file = new DatastoreFile(diskChain[0]);

            if (managed) {
                DatastoreFile originalFile = new DatastoreFile(volumeTO.getPath());

                if (!file.getFileBaseName().equalsIgnoreCase(originalFile.getFileBaseName())) {
                    if (s_logger.isInfoEnabled())
                        s_logger.info("Detected disk-chain top file change on volume: " + volumeTO.getId() + " " + volumeTO.getPath() + " -> " + diskChain[0]);
                }
            }
            else {
                if (!file.getFileBaseName().equalsIgnoreCase(volumeTO.getPath())) {
                    if (s_logger.isInfoEnabled())
                        s_logger.info("Detected disk-chain top file change on volume: " + volumeTO.getId() + " " + volumeTO.getPath() + " -> " + file.getFileBaseName());
                }
            }

            VolumeObjectTO volInSpec = getVolumeInSpec(vmSpec, volumeTO);
            if (volInSpec != null) {
                if (managed) {
                    String datastoreVolumePath = diskChain[0];
                    iqnToPath.put(details.get(DiskTO.IQN), datastoreVolumePath);
                    vol.setPath(datastoreVolumePath);
                    volumeTO.setPath(datastoreVolumePath);
                    volInSpec.setPath(datastoreVolumePath);
                } else {
                    volInSpec.setPath(file.getFileBaseName());
                }
                volInSpec.setChainInfo(_gson.toJson(diskInfo));
            }
        }
    }

    private static VolumeObjectTO getVolumeInSpec(VirtualMachineTO vmSpec, VolumeObjectTO srcVol) {
        for (DiskTO disk : vmSpec.getDisks()) {
            VolumeObjectTO vol = (VolumeObjectTO)disk.getData();
            if (vol.getId() == srcVol.getId())
                return vol;
        }

        return null;
    }

    private static NicTO[] sortNicsByDeviceId(NicTO[] nics) {

        List<NicTO> listForSort = new ArrayList<NicTO>();
        for (NicTO nic : nics) {
            listForSort.add(nic);
        }
        Collections.sort(listForSort, new Comparator<NicTO>() {

            @Override
            public int compare(NicTO arg0, NicTO arg1) {
                if (arg0.getDeviceId() < arg1.getDeviceId()) {
                    return -1;
                } else if (arg0.getDeviceId() == arg1.getDeviceId()) {
                    return 0;
                }

                return 1;
            }
        });

        return listForSort.toArray(new NicTO[0]);
    }

    private static DiskTO[] sortVolumesByDeviceId(DiskTO[] volumes) {

        List<DiskTO> listForSort = new ArrayList<DiskTO>();
        for (DiskTO vol : volumes) {
            listForSort.add(vol);
        }
        Collections.sort(listForSort, new Comparator<DiskTO>() {

            @Override
            public int compare(DiskTO arg0, DiskTO arg1) {
                if (arg0.getDiskSeq() < arg1.getDiskSeq()) {
                    return -1;
                } else if (arg0.getDiskSeq().equals(arg1.getDiskSeq())) {
                    return 0;
                }

                return 1;
            }
        });

        return listForSort.toArray(new DiskTO[0]);
    }

    private HashMap<String, Pair<ManagedObjectReference, DatastoreMO>> inferDatastoreDetailsFromDiskInfo(VmwareHypervisorHost hyperHost, VmwareContext context,
            DiskTO[] disks, Command cmd) throws Exception {
        HashMap<String, Pair<ManagedObjectReference, DatastoreMO>> mapIdToMors = new HashMap<String, Pair<ManagedObjectReference, DatastoreMO>>();

        assert (hyperHost != null) && (context != null);

        for (DiskTO vol : disks) {
            if (vol.getType() != Volume.Type.ISO) {
                VolumeObjectTO volumeTO = (VolumeObjectTO)vol.getData();
                DataStoreTO primaryStore = volumeTO.getDataStore();
                String poolUuid = primaryStore.getUuid();

                if (mapIdToMors.get(poolUuid) == null) {
                    boolean isManaged = false;
                    Map<String, String> details = vol.getDetails();

                    if (details != null) {
                        isManaged = Boolean.parseBoolean(details.get(DiskTO.MANAGED));
                    }

                    if (isManaged) {
                        String iScsiName = details.get(DiskTO.IQN); // details should not be null for managed storage (it may or may not be null for non-managed storage)
                        String datastoreName = VmwareResource.getDatastoreName(iScsiName);
                        ManagedObjectReference morDatastore = HypervisorHostHelper.findDatastoreWithBackwardsCompatibility(hyperHost, datastoreName);

                        // if the datastore is not present, we need to discover the iSCSI device that will support it,
                        // create the datastore, and create a VMDK file in the datastore
                        if (morDatastore == null) {
                            morDatastore = _storageProcessor.prepareManagedStorage(context, hyperHost, iScsiName,
                                    details.get(DiskTO.STORAGE_HOST), Integer.parseInt(details.get(DiskTO.STORAGE_PORT)),
                                    volumeTO.getVolumeType() == Volume.Type.ROOT ? volumeTO.getName() : null,
                                    details.get(DiskTO.CHAP_INITIATOR_USERNAME), details.get(DiskTO.CHAP_INITIATOR_SECRET),
                                    details.get(DiskTO.CHAP_TARGET_USERNAME), details.get(DiskTO.CHAP_TARGET_SECRET),
                                    Long.parseLong(details.get(DiskTO.VOLUME_SIZE)), cmd);

                            DatastoreMO dsMo = new DatastoreMO(getServiceContext(), morDatastore);
                            String datastoreVolumePath = dsMo.getDatastorePath((volumeTO.getVolumeType() == Volume.Type.ROOT ? volumeTO.getName() : dsMo.getName()) + ".vmdk");

                            volumeTO.setPath(datastoreVolumePath);
                            vol.setPath(datastoreVolumePath);
                        }

                        mapIdToMors.put(datastoreName, new Pair<ManagedObjectReference, DatastoreMO>(morDatastore, new DatastoreMO(context, morDatastore)));
                    }
                    else {
                        ManagedObjectReference morDatastore = HypervisorHostHelper.findDatastoreWithBackwardsCompatibility(hyperHost, poolUuid);

                        if (morDatastore == null) {
                            String msg = "Failed to get the mounted datastore for the volume's pool " + poolUuid;

                            s_logger.error(msg);

                            throw new Exception(msg);
                        }

                        mapIdToMors.put(poolUuid, new Pair<ManagedObjectReference, DatastoreMO>(morDatastore, new DatastoreMO(context, morDatastore)));
                    }
                }
            }
        }

        return mapIdToMors;
    }

    private DatastoreMO getDatastoreThatRootDiskIsOn(HashMap<String, Pair<ManagedObjectReference, DatastoreMO>> dataStoresDetails, DiskTO disks[]) {
        Pair<ManagedObjectReference, DatastoreMO> rootDiskDataStoreDetails = null;

        for (DiskTO vol : disks) {
            if (vol.getType() == Volume.Type.ROOT) {
                Map<String, String> details = vol.getDetails();
                boolean managed = false;

                if (details != null) {
                    managed = Boolean.parseBoolean(details.get(DiskTO.MANAGED));
                }

                if (managed) {
                    String datastoreName = VmwareResource.getDatastoreName(details.get(DiskTO.IQN));

                    rootDiskDataStoreDetails = dataStoresDetails.get(datastoreName);

                    break;
                }
                else {
                    DataStoreTO primaryStore = vol.getData().getDataStore();

                    rootDiskDataStoreDetails = dataStoresDetails.get(primaryStore.getUuid());

                    break;
                }
            }
        }

        if (rootDiskDataStoreDetails != null) {
            return rootDiskDataStoreDetails.second();
        }

        return null;
    }

    private String getPvlanInfo(NicTO nicTo) {
        if (nicTo.getBroadcastType() == BroadcastDomainType.Pvlan) {
            return NetUtils.getIsolatedPvlanFromUri(nicTo.getBroadcastUri());
        }
        return null;
    }

    private String getVlanInfo(NicTO nicTo, String defaultVlan) {
        if (nicTo.getBroadcastType() == BroadcastDomainType.Native) {
            return defaultVlan;
        } else if (nicTo.getBroadcastType() == BroadcastDomainType.Vlan || nicTo.getBroadcastType() == BroadcastDomainType.Pvlan) {
            if (nicTo.getBroadcastUri() != null) {
                if (nicTo.getBroadcastType() == BroadcastDomainType.Vlan)
                    // For vlan, the broadcast uri is of the form vlan://<vlanid>
                    // BroadcastDomainType recogniizes and handles this.
                    return BroadcastDomainType.getValue(nicTo.getBroadcastUri());
                else
                    // for pvlan, the broacast uri will be of the form pvlan://<vlanid>-i<pvlanid>
                    // TODO consider the spread of functionality between BroadcastDomainType and NetUtils
                    return NetUtils.getPrimaryPvlanFromUri(nicTo.getBroadcastUri());
            } else {
                s_logger.warn("BroadcastType is not claimed as VLAN or PVLAN, but without vlan info in broadcast URI. Use vlan info from labeling: " + defaultVlan);
                return defaultVlan;
            }
        } else if (nicTo.getBroadcastType() == BroadcastDomainType.Lswitch) {
            // We don't need to set any VLAN id for an NVP logical switch
            return null;
        }

        s_logger.warn("Unrecognized broadcast type in VmwareResource, type: " + nicTo.getBroadcastType().toString() + ". Use vlan info from labeling: " + defaultVlan);
        return defaultVlan;
    }

    private Pair<ManagedObjectReference, String> prepareNetworkFromNicInfo(HostMO hostMo, NicTO nicTo, boolean configureVServiceInNexus, VirtualMachine.Type vmType) throws Exception {

        Ternary<String, String, String> switchDetails = getTargetSwitch(nicTo);
        nicTo.getType();
        VirtualSwitchType switchType = VirtualSwitchType.getType(switchDetails.second());
        String switchName = switchDetails.first();
        String vlanToken = switchDetails.third();

        String namePrefix = getNetworkNamePrefix(nicTo);
        Pair<ManagedObjectReference, String> networkInfo = null;

        s_logger.info("Prepare network on " + switchType + " " + switchName + " with name prefix: " + namePrefix);

        if (VirtualSwitchType.StandardVirtualSwitch == switchType) {
            synchronized(hostMo.getMor().getValue().intern()) {
                networkInfo = HypervisorHostHelper.prepareNetwork(switchName, namePrefix, hostMo, getVlanInfo(nicTo, vlanToken), nicTo.getNetworkRateMbps(),
                        nicTo.getNetworkRateMulticastMbps(), _opsTimeout,
                        !namePrefix.startsWith("cloud.private"), nicTo.getBroadcastType(), nicTo.getUuid());
            }
        }
        else {
            String vlanId = getVlanInfo(nicTo, vlanToken);
            String svlanId = null;
            boolean pvlannetwork = (getPvlanInfo(nicTo) == null) ? false : true;
            if (vmType != null && vmType.equals(VirtualMachine.Type.DomainRouter) && pvlannetwork) {
                // plumb this network to the promiscuous vlan.
                svlanId = vlanId;
            } else {
                // plumb this network to the isolated vlan.
                svlanId = getPvlanInfo(nicTo);
            }
            networkInfo = HypervisorHostHelper.prepareNetwork(switchName, namePrefix, hostMo, vlanId, svlanId,
                    nicTo.getNetworkRateMbps(), nicTo.getNetworkRateMulticastMbps(), _opsTimeout, switchType,
                    _portsPerDvPortGroup, nicTo.getGateway(), configureVServiceInNexus, nicTo.getBroadcastType(), _vsmCredentials);
        }

        return networkInfo;
    }

    // return Ternary <switch name, switch tyep, vlan tagging>
    private Ternary<String, String, String> getTargetSwitch(NicTO nicTo) throws CloudException {
        TrafficType[] supportedTrafficTypes =
                new TrafficType[] {
                TrafficType.Guest,
                TrafficType.Public,
                TrafficType.Control,
                TrafficType.Management,
                TrafficType.Storage
        };

        TrafficType trafficType = nicTo.getType();
        if (!Arrays.asList(supportedTrafficTypes).contains(trafficType)) {
            throw new CloudException("Traffic type " + trafficType.toString() + " for nic " + nicTo.toString() + " is not supported.");
        }

        String switchName = null;
        VirtualSwitchType switchType = VirtualSwitchType.StandardVirtualSwitch;
        String vlanToken = Vlan.UNTAGGED;

        // Get switch details from the nicTO object
        if(nicTo.getName() != null && !nicTo.getName().isEmpty()) {
            String[] tokens = nicTo.getName().split(",");
            // Format of network traffic label is <VSWITCH>,<VLANID>,<VSWITCHTYPE>
            // If all 3 fields are mentioned then number of tokens would be 3.
            // If only <VSWITCH>,<VLANID> are mentioned then number of tokens would be 2.
            switchName = tokens[0];
            if(tokens.length == 2 || tokens.length == 3) {
                vlanToken = tokens[1];
                if (vlanToken.isEmpty()) {
                    vlanToken = Vlan.UNTAGGED;
                }
                if (tokens.length == 3) {
                    switchType = VirtualSwitchType.getType(tokens[2]);
                }
            }
        } else {
            if (trafficType == TrafficType.Guest && _guestTrafficInfo != null) {
                switchType = _guestTrafficInfo.getVirtualSwitchType();
                switchName = _guestTrafficInfo.getVirtualSwitchName();
            } else if (trafficType == TrafficType.Public && _publicTrafficInfo != null) {
                switchType = _publicTrafficInfo.getVirtualSwitchType();
                switchName = _publicTrafficInfo.getVirtualSwitchName();
            }
        }

        if (switchName == null
                && (nicTo.getType() == Networks.TrafficType.Control || nicTo.getType() == Networks.TrafficType.Management || nicTo.getType() == Networks.TrafficType.Storage)) {
            switchName = _privateNetworkVSwitchName;
        }

        return new Ternary<String,String,String>(switchName, switchType.toString(), vlanToken);
    }

    private String getNetworkNamePrefix(NicTO nicTo) throws Exception {
        if (nicTo.getType() == Networks.TrafficType.Guest) {
            return "cloud.guest";
        } else if (nicTo.getType() == Networks.TrafficType.Control || nicTo.getType() == Networks.TrafficType.Management) {
            return "cloud.private";
        } else if (nicTo.getType() == Networks.TrafficType.Public) {
            return "cloud.public";
        } else if (nicTo.getType() == Networks.TrafficType.Storage) {
            return "cloud.storage";
        } else if (nicTo.getType() == Networks.TrafficType.Vpn) {
            throw new Exception("Unsupported traffic type: " + nicTo.getType().toString());
        } else {
            throw new Exception("Unsupported traffic type: " + nicTo.getType().toString());
        }
    }

    private VirtualMachineMO takeVmFromOtherHyperHost(VmwareHypervisorHost hyperHost, String vmName) throws Exception {

        VirtualMachineMO vmMo = hyperHost.findVmOnPeerHyperHost(vmName);
        if (vmMo != null) {
            ManagedObjectReference morTargetPhysicalHost = hyperHost.findMigrationTarget(vmMo);
            if (morTargetPhysicalHost == null) {
                String msg = "VM " + vmName + " is on other host and we have no resource available to migrate and start it here";
                s_logger.error(msg);
                throw new Exception(msg);
            }

            if (!vmMo.relocate(morTargetPhysicalHost)) {
                String msg = "VM " + vmName + " is on other host and we failed to relocate it here";
                s_logger.error(msg);
                throw new Exception(msg);
            }

            return vmMo;
        }
        return null;
    }

    // isoUrl sample content :
    // nfs://192.168.10.231/export/home/kelven/vmware-test/secondary/template/tmpl/2/200//200-2-80f7ee58-6eff-3a2d-bcb0-59663edf6d26.iso
    private Pair<String, ManagedObjectReference> getIsoDatastoreInfo(VmwareHypervisorHost hyperHost, String isoUrl) throws Exception {

        assert (isoUrl != null);
        int isoFileNameStartPos = isoUrl.lastIndexOf("/");
        if (isoFileNameStartPos < 0) {
            throw new Exception("Invalid ISO path info");
        }

        String isoFileName = isoUrl.substring(isoFileNameStartPos);

        int templateRootPos = isoUrl.indexOf("template/tmpl");
        if (templateRootPos < 0) {
            throw new Exception("Invalid ISO path info");
        }

        String storeUrl = isoUrl.substring(0, templateRootPos - 1);
        String isoPath = isoUrl.substring(templateRootPos, isoFileNameStartPos);

        ManagedObjectReference morDs = prepareSecondaryDatastoreOnHost(storeUrl);
        DatastoreMO dsMo = new DatastoreMO(getServiceContext(), morDs);

        return new Pair<String, ManagedObjectReference>(String.format("[%s] %s%s", dsMo.getName(), isoPath, isoFileName), morDs);
    }

    protected Answer execute(ReadyCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource ReadyCommand: " + _gson.toJson(cmd));
        }

        try {
            VmwareContext context = getServiceContext();
            VmwareHypervisorHost hyperHost = getHyperHost(context);
            if (hyperHost.isHyperHostConnected()) {
                return new ReadyAnswer(cmd);
            } else {
                return new ReadyAnswer(cmd, "Host is not in connect state");
            }
        } catch (Exception e) {
            s_logger.error("Unexpected exception: ", e);
            return new ReadyAnswer(cmd, VmwareHelper.getExceptionMessage(e));
        }
    }

    protected Answer execute(GetHostStatsCommand cmd) {
        if (s_logger.isTraceEnabled()) {
            s_logger.trace("Executing resource GetHostStatsCommand: " + _gson.toJson(cmd));
        }

        VmwareContext context = getServiceContext();
        VmwareHypervisorHost hyperHost = getHyperHost(context);

        HostStatsEntry hostStats = new HostStatsEntry(cmd.getHostId(), 0, 0, 0, "host", 0, 0, 0, 0);
        Answer answer = new GetHostStatsAnswer(cmd, hostStats);
        try {
            HostStatsEntry entry = getHyperHostStats(hyperHost);
            if (entry != null) {
                entry.setHostId(cmd.getHostId());
                answer = new GetHostStatsAnswer(cmd, entry);
            }
        } catch (Exception e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }

            String msg = "Unable to execute GetHostStatsCommand due to " + VmwareHelper.getExceptionMessage(e);
            s_logger.error(msg, e);
        }

        if (s_logger.isTraceEnabled()) {
            s_logger.trace("GetHostStats Answer: " + _gson.toJson(answer));
        }

        return answer;
    }

    protected Answer execute(GetVmStatsCommand cmd) {
        if (s_logger.isTraceEnabled()) {
            s_logger.trace("Executing resource GetVmStatsCommand: " + _gson.toJson(cmd));
        }

        HashMap<String, VmStatsEntry> vmStatsMap = null;

        try {
            HashMap<String, PowerState> vmPowerStates = getVmStates();

            // getVmNames should return all i-x-y values.
            List<String> requestedVmNames = cmd.getVmNames();
            List<String> vmNames = new ArrayList<String>();

            if (requestedVmNames != null) {
                for (String vmName : requestedVmNames) {
                    if (vmPowerStates.get(vmName) != null) {
                        vmNames.add(vmName);
                    }
                }
            }

            if (vmNames != null) {
                vmStatsMap = getVmStats(vmNames);
            }
        } catch (Throwable e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }

            s_logger.error("Unable to execute GetVmStatsCommand due to : " + VmwareHelper.getExceptionMessage(e), e);
        }

        Answer answer = new GetVmStatsAnswer(cmd, vmStatsMap);

        if (s_logger.isTraceEnabled()) {
            s_logger.trace("Report GetVmStatsAnswer: " + _gson.toJson(answer));
        }
        return answer;
    }

    protected Answer execute(GetVmDiskStatsCommand cmd) {
        return new GetVmDiskStatsAnswer(cmd, null, null, null);
    }

    protected Answer execute(CheckHealthCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource CheckHealthCommand: " + _gson.toJson(cmd));
        }

        try {
            VmwareHypervisorHost hyperHost = getHyperHost(getServiceContext());
            if (hyperHost.isHyperHostConnected()) {
                return new CheckHealthAnswer(cmd, true);
            }
        } catch (Throwable e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }

            s_logger.error("Unable to execute CheckHealthCommand due to " + VmwareHelper.getExceptionMessage(e), e);
        }
        return new CheckHealthAnswer(cmd, false);
    }

    protected Answer execute(StopCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource StopCommand: " + _gson.toJson(cmd));
        }

        // In the stop command, we're passed in the name of the VM as seen by cloudstack,
        // i.e., i-x-y. This is the internal VM name.
        VmwareContext context = getServiceContext();
        VmwareHypervisorHost hyperHost = getHyperHost(context);
        try {
            VirtualMachineMO vmMo = hyperHost.findVmOnHyperHost(cmd.getVmName());
            if (vmMo != null) {
                if (cmd.checkBeforeCleanup()) {
                    if (getVmPowerState(vmMo) != PowerState.PowerOff) {
                        String msg = "StopCommand is sent for cleanup and VM " + cmd.getVmName() + " is current running. ignore it.";
                        s_logger.warn(msg);
                        return new StopAnswer(cmd, msg, false);
                    } else {
                        String msg = "StopCommand is sent for cleanup and VM " + cmd.getVmName() + " is indeed stopped already.";
                        s_logger.info(msg);
                        return new StopAnswer(cmd, msg, true);
                    }
                }

                try {
                    vmMo.setCustomFieldValue(CustomFieldConstants.CLOUD_NIC_MASK, "0");

                    if (getVmPowerState(vmMo) != PowerState.PowerOff) {
                        if (vmMo.safePowerOff(_shutdownWaitMs)) {
                            return new StopAnswer(cmd, "Stop VM " + cmd.getVmName() + " Succeed", true);
                        } else {
                            String msg = "Have problem in powering off VM " + cmd.getVmName() + ", let the process continue";
                            s_logger.warn(msg);
                            return new StopAnswer(cmd, msg, true);
                        }
                    }

                    String msg = "VM " + cmd.getVmName() + " is already in stopped state";
                    s_logger.info(msg);
                    return new StopAnswer(cmd, msg, true);
                } finally {
                }
            } else {

                String msg = "VM " + cmd.getVmName() + " is no longer in vSphere";
                s_logger.info(msg);
                return new StopAnswer(cmd, msg, true);
            }
        } catch (Exception e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }

            String msg = "StopCommand failed due to " + VmwareHelper.getExceptionMessage(e);
            s_logger.error(msg);
            return new StopAnswer(cmd, msg, false);
        }
    }

    protected Answer execute(RebootRouterCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource RebootRouterCommand: " + _gson.toJson(cmd));
        }

        RebootAnswer answer = (RebootAnswer)execute((RebootCommand)cmd);

        if (answer.getResult()) {
            String connectResult = connect(cmd.getVmName(), cmd.getPrivateIpAddress());
            networkUsage(cmd.getPrivateIpAddress(), "create", null);
            if (connectResult == null) {
                return answer;
            } else {
                return new Answer(cmd, false, connectResult);
            }
        }
        return answer;
    }

    protected Answer execute(RebootCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource RebootCommand: " + _gson.toJson(cmd));
        }

        VmwareContext context = getServiceContext();
        VmwareHypervisorHost hyperHost = getHyperHost(context);
        try {
            VirtualMachineMO vmMo = hyperHost.findVmOnHyperHost(cmd.getVmName());
            if (vmMo != null) {
                try {
                    vmMo.rebootGuest();
                    return new RebootAnswer(cmd, "reboot succeeded", true);
                } catch (ToolsUnavailableFaultMsg e) {
                    s_logger.warn("VMware tools is not installed at guest OS, we will perform hard reset for reboot");
                } catch (Exception e) {
                    s_logger.warn("We are not able to perform gracefull guest reboot due to " + VmwareHelper.getExceptionMessage(e));
                }

                // continue to try with hard-reset
                if (vmMo.reset()) {
                    return new RebootAnswer(cmd, "reboot succeeded", true);
                }

                String msg = "Reboot failed in vSphere. vm: " + cmd.getVmName();
                s_logger.warn(msg);
                return new RebootAnswer(cmd, msg, false);
            } else {
                String msg = "Unable to find the VM in vSphere to reboot. vm: " + cmd.getVmName();
                s_logger.warn(msg);
                return new RebootAnswer(cmd, msg, false);
            }
        } catch (Exception e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }

            String msg = "RebootCommand failed due to " + VmwareHelper.getExceptionMessage(e);
            s_logger.error(msg);
            return new RebootAnswer(cmd, msg, false);
        }
    }

    protected Answer execute(CheckVirtualMachineCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource CheckVirtualMachineCommand: " + _gson.toJson(cmd));
        }

        final String vmName = cmd.getVmName();
        PowerState powerState = PowerState.PowerUnknown;
        Integer vncPort = null;

        VmwareContext context = getServiceContext();
        VmwareHypervisorHost hyperHost = getHyperHost(context);

        try {
            VirtualMachineMO vmMo = hyperHost.findVmOnHyperHost(vmName);
            if (vmMo != null) {
                powerState = getVmPowerState(vmMo);
                return new CheckVirtualMachineAnswer(cmd, powerState, vncPort);
            } else {
                s_logger.warn("Can not find vm " + vmName + " to execute CheckVirtualMachineCommand");
                return new CheckVirtualMachineAnswer(cmd, powerState, vncPort);
            }

        } catch (Throwable e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }
            s_logger.error("Unexpected exception: " + VmwareHelper.getExceptionMessage(e), e);

            return new CheckVirtualMachineAnswer(cmd, powerState, vncPort);
        }
    }

    protected Answer execute(PrepareForMigrationCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource PrepareForMigrationCommand: " + _gson.toJson(cmd));
        }

        VirtualMachineTO vm = cmd.getVirtualMachine();
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Preparing host for migrating " + vm);
        }

        final String vmName = vm.getName();
        try {
            VmwareHypervisorHost hyperHost = getHyperHost(getServiceContext());
            VmwareManager mgr = hyperHost.getContext().getStockObject(VmwareManager.CONTEXT_STOCK_NAME);

            // find VM through datacenter (VM is not at the target host yet)
            VirtualMachineMO vmMo = hyperHost.findVmOnPeerHyperHost(vmName);
            if (vmMo == null) {
                s_logger.info("VM " + vmName + " was not found in the cluster of host " + hyperHost.getHyperHostName() + ". Looking for the VM in datacenter.");
                ManagedObjectReference dcMor = hyperHost.getHyperHostDatacenter();
                DatacenterMO dcMo = new DatacenterMO(hyperHost.getContext(), dcMor);
                vmMo = dcMo.findVm(vmName);
                if (vmMo == null) {
                    String msg = "VM " + vmName + " does not exist in VMware datacenter";
                    s_logger.error(msg);
                    throw new Exception(msg);
                }
            }

            NicTO[] nics = vm.getNics();
            for (NicTO nic : nics) {
                // prepare network on the host
                prepareNetworkFromNicInfo(new HostMO(getServiceContext(), _morHyperHost), nic, false, cmd.getVirtualMachine().getType());
            }

            String secStoreUrl = mgr.getSecondaryStorageStoreUrl(Long.parseLong(_dcId));
            if (secStoreUrl == null) {
                String msg = "secondary storage for dc " + _dcId + " is not ready yet?";
                throw new Exception(msg);
            }
            mgr.prepareSecondaryStorageStore(secStoreUrl);

            ManagedObjectReference morSecDs = prepareSecondaryDatastoreOnHost(secStoreUrl);
            if (morSecDs == null) {
                String msg = "Failed to prepare secondary storage on host, secondary store url: " + secStoreUrl;
                throw new Exception(msg);
            }
            return new PrepareForMigrationAnswer(cmd);
        } catch (Throwable e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }

            String msg = "Unexcpeted exception " + VmwareHelper.getExceptionMessage(e);
            s_logger.error(msg, e);
            return new PrepareForMigrationAnswer(cmd, msg);
        }
    }

    protected Answer execute(MigrateCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource MigrateCommand: " + _gson.toJson(cmd));
        }

        final String vmName = cmd.getVmName();
        try {
            VmwareHypervisorHost hyperHost = getHyperHost(getServiceContext());
            ManagedObjectReference morDc = hyperHost.getHyperHostDatacenter();

            // find VM through datacenter (VM is not at the target host yet)
            VirtualMachineMO vmMo = hyperHost.findVmOnPeerHyperHost(vmName);
            if (vmMo == null) {
                String msg = "VM " + vmName + " does not exist in VMware datacenter";
                s_logger.error(msg);
                throw new Exception(msg);
            }

            VmwareHypervisorHost destHyperHost = getTargetHyperHost(new DatacenterMO(hyperHost.getContext(), morDc), cmd.getDestinationIp());

            ManagedObjectReference morTargetPhysicalHost = destHyperHost.findMigrationTarget(vmMo);
            if (morTargetPhysicalHost == null) {
                throw new Exception("Unable to find a target capable physical host");
            }

            if (!vmMo.migrate(destHyperHost.getHyperHostOwnerResourcePool(), morTargetPhysicalHost)) {
                throw new Exception("Migration failed");
            }

            return new MigrateAnswer(cmd, true, "migration succeeded", null);
        } catch (Throwable e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }

            String msg = "MigrationCommand failed due to " + VmwareHelper.getExceptionMessage(e);
            s_logger.warn(msg, e);
            return new MigrateAnswer(cmd, false, msg, null);
        }
    }

    protected Answer execute(MigrateWithStorageCommand cmd) {

        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource MigrateWithStorageCommand: " + _gson.toJson(cmd));
        }

        VirtualMachineTO vmTo = cmd.getVirtualMachine();
        String vmName = vmTo.getName();

        VmwareHypervisorHost srcHyperHost = null;
        VmwareHypervisorHost tgtHyperHost = null;
        VirtualMachineMO vmMo = null;

        ManagedObjectReference morDsAtTarget = null;
        ManagedObjectReference morDsAtSource = null;
        ManagedObjectReference morDc = null;
        ManagedObjectReference morDcOfTargetHost = null;
        ManagedObjectReference morTgtHost = new ManagedObjectReference();
        VirtualMachineRelocateSpec relocateSpec = new VirtualMachineRelocateSpec();
        List<VirtualMachineRelocateSpecDiskLocator> diskLocators = new ArrayList<VirtualMachineRelocateSpecDiskLocator>();
        VirtualMachineRelocateSpecDiskLocator diskLocator = null;

        boolean isFirstDs = true;
        String tgtDsName = "";
        String tgtDsNfsHost;
        String tgtDsNfsPath;
        int tgtDsNfsPort;
        VolumeTO volume;
        StorageFilerTO filerTo;
        Set<String> mountedDatastoresAtSource = new HashSet<String>();
        List<VolumeObjectTO> volumeToList =  new ArrayList<VolumeObjectTO>();
        Map<Long, Integer> volumeDeviceKey = new HashMap<Long, Integer>();

        Map<VolumeTO, StorageFilerTO> volToFiler = cmd.getVolumeToFiler();
        String tgtHost = cmd.getTargetHost();
        String tgtHostMorInfo = tgtHost.split("@")[0];
        morTgtHost.setType(tgtHostMorInfo.split(":")[0]);
        morTgtHost.setValue(tgtHostMorInfo.split(":")[1]);

        try {
            srcHyperHost = getHyperHost(getServiceContext());
            tgtHyperHost = new HostMO(getServiceContext(), morTgtHost);
            morDc = srcHyperHost.getHyperHostDatacenter();
            morDcOfTargetHost = tgtHyperHost.getHyperHostDatacenter();
            if (!morDc.getValue().equalsIgnoreCase(morDcOfTargetHost.getValue())) {
                String msg = "Source host & target host are in different datacentesr";
                throw new CloudRuntimeException(msg);
            }
            VmwareManager mgr = tgtHyperHost.getContext().getStockObject(VmwareManager.CONTEXT_STOCK_NAME);

            // find VM through datacenter (VM is not at the target host yet)
            vmMo = srcHyperHost.findVmOnPeerHyperHost(vmName);
            if (vmMo == null) {
                String msg = "VM " + vmName + " does not exist in VMware datacenter " + morDc.getValue();
                s_logger.error(msg);
                throw new Exception(msg);
            }
            vmName = vmMo.getName();

            // Get details of each target datastore & attach to source host.
            for (Entry<VolumeTO, StorageFilerTO> entry : volToFiler.entrySet()) {
                volume = entry.getKey();
                filerTo = entry.getValue();

                tgtDsName = filerTo.getUuid().replace("-", "");
                tgtDsNfsHost = filerTo.getHost();
                tgtDsNfsPath = filerTo.getPath();
                tgtDsNfsPort = filerTo.getPort();

                s_logger.debug("Preparing spec for volume : " + volume.getName());
                morDsAtTarget = HypervisorHostHelper.findDatastoreWithBackwardsCompatibility(tgtHyperHost, filerTo.getUuid());
                if (morDsAtTarget == null) {
                    String msg = "Unable to find the mounted datastore with uuid " + morDsAtTarget + " to execute MigrateWithStorageCommand";
                    s_logger.error(msg);
                    throw new Exception(msg);
                }
                morDsAtSource = HypervisorHostHelper.findDatastoreWithBackwardsCompatibility(srcHyperHost, filerTo.getUuid());
                if (morDsAtSource == null) {
                    morDsAtSource = srcHyperHost.mountDatastore(false, tgtDsNfsHost, tgtDsNfsPort, tgtDsNfsPath, tgtDsName);
                    if (morDsAtSource == null) {
                        throw new Exception("Unable to mount datastore " + tgtDsNfsHost + ":/" + tgtDsNfsPath + " on " + _hostName);
                    }
                    mountedDatastoresAtSource.add(tgtDsName);
                    s_logger.debug("Mounted datastore " + tgtDsNfsHost + ":/" + tgtDsNfsPath + " on " + _hostName);
                }

                if (isFirstDs) {
                    relocateSpec.setDatastore(morDsAtSource);
                    isFirstDs = false;
                }
                VmwareStorageLayoutHelper.getVmwareDatastorePathFromVmdkFileName(new DatastoreMO(srcHyperHost.getContext(), morDsAtSource), vmName, volume.getPath() +
                        ".vmdk");
                diskLocator = new VirtualMachineRelocateSpecDiskLocator();
                diskLocator.setDatastore(morDsAtSource);
                int diskId = getVirtualDiskInfo(vmMo, volume.getPath() + ".vmdk");
                diskLocator.setDiskId(diskId);

                diskLocators.add(diskLocator);
                volumeDeviceKey.put(volume.getId(), diskId);

            }
            relocateSpec.getDisk().addAll(diskLocators);

            // Prepare network at target before migration
            NicTO[] nics = vmTo.getNics();
            for (NicTO nic : nics) {
                // prepare network on the host
                prepareNetworkFromNicInfo(new HostMO(getServiceContext(), morTgtHost), nic, false, vmTo.getType());
            }

            // Ensure secondary storage mounted on target host
            String secStoreUrl = mgr.getSecondaryStorageStoreUrl(Long.parseLong(_dcId));
            if (secStoreUrl == null) {
                String msg = "secondary storage for dc " + _dcId + " is not ready yet?";
                throw new Exception(msg);
            }
            mgr.prepareSecondaryStorageStore(secStoreUrl);
            ManagedObjectReference morSecDs = prepareSecondaryDatastoreOnHost(secStoreUrl);
            if (morSecDs == null) {
                String msg = "Failed to prepare secondary storage on host, secondary store url: " + secStoreUrl;
                throw new Exception(msg);
            }

            // Change datastore
            if (!vmMo.changeDatastore(relocateSpec)) {
                throw new Exception("Change datastore operation failed during storage migration");
            } else {
                s_logger.debug("Successfully migrated storage of VM " + vmName + " to target datastore(s)");
            }

            // Update and return volume path for every disk because that could have changed after migration
            for (Entry<VolumeTO, StorageFilerTO> entry : volToFiler.entrySet()) {
                volume = entry.getKey();
                long volumeId = volume.getId();
                VirtualDisk[] disks = vmMo.getAllDiskDevice();
                for (VirtualDisk disk : disks) {
                    if (volumeDeviceKey.get(volumeId) == disk.getKey()) {
                        VolumeObjectTO newVol = new VolumeObjectTO();
                        newVol.setId(volumeId);
                        newVol.setPath(vmMo.getVmdkFileBaseName(disk));
                        volumeToList.add(newVol);
                        break;
                    }
                }
            }

            // Change host
            ManagedObjectReference morPool = tgtHyperHost.getHyperHostOwnerResourcePool();
            if (!vmMo.migrate(morPool, tgtHyperHost.getMor())) {
                throw new Exception("Change datastore operation failed during storage migration");
            } else {
                s_logger.debug("Successfully relocated VM " + vmName + " from " + _hostName + " to " + tgtHyperHost.getHyperHostName());
            }

            return new MigrateWithStorageAnswer(cmd, volumeToList);
        } catch (Throwable e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encountered remote exception at vCenter, invalidating VMware session context");
                invalidateServiceContext();
            }

            String msg = "MigrationCommand failed due to " + VmwareHelper.getExceptionMessage(e);
            s_logger.warn(msg, e);
            return new MigrateWithStorageAnswer(cmd, (Exception)e);
        } finally {
            // Cleanup datastores mounted on source host
            for (String mountedDatastore : mountedDatastoresAtSource) {
                s_logger.debug("Attempting to unmount datastore " + mountedDatastore + " at " + _hostName);
                try {
                    srcHyperHost.unmountDatastore(mountedDatastore);
                } catch (Exception unmountEx) {
                    s_logger.debug("Failed to unmount datastore " + mountedDatastore + " at " + _hostName + ". Seems the datastore is still being used by " + _hostName +
                            ". Please unmount manually to cleanup.");
                }
                s_logger.debug("Successfully unmounted datastore " + mountedDatastore + " at " + _hostName);
            }
        }
    }

    private Answer execute(MigrateVolumeCommand cmd) {
        String volumePath = cmd.getVolumePath();
        StorageFilerTO poolTo = cmd.getPool();

        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource MigrateVolumeCommand: " + _gson.toJson(cmd));
        }

        String vmName = cmd.getAttachedVmName();

        VirtualMachineMO vmMo = null;
        VmwareHypervisorHost srcHyperHost = null;

        ManagedObjectReference morDs = null;
        ManagedObjectReference morDc = null;
        VirtualMachineRelocateSpec relocateSpec = new VirtualMachineRelocateSpec();
        List<VirtualMachineRelocateSpecDiskLocator> diskLocators = new ArrayList<VirtualMachineRelocateSpecDiskLocator>();
        VirtualMachineRelocateSpecDiskLocator diskLocator = null;

        String tgtDsName = "";

        try {
            srcHyperHost = getHyperHost(getServiceContext());
            morDc = srcHyperHost.getHyperHostDatacenter();
            tgtDsName = poolTo.getUuid().replace("-", "");

            // find VM in this datacenter not just in this cluster.
            DatacenterMO dcMo = new DatacenterMO(getServiceContext(), morDc);
            vmMo = dcMo.findVm(vmName);

            if (vmMo == null) {
                String msg = "VM " + vmName + " does not exist in VMware datacenter " + morDc.getValue();
                s_logger.error(msg);
                throw new Exception(msg);
            }
            vmName = vmMo.getName();
            morDs = HypervisorHostHelper.findDatastoreWithBackwardsCompatibility(srcHyperHost, tgtDsName);
            if (morDs == null) {
                String msg = "Unable to find the mounted datastore with name " + tgtDsName + " to execute MigrateVolumeCommand";
                s_logger.error(msg);
                throw new Exception(msg);
            }

            DatastoreMO targetDsMo = new DatastoreMO(srcHyperHost.getContext(), morDs);
            String fullVolumePath = VmwareStorageLayoutHelper.getVmwareDatastorePathFromVmdkFileName(targetDsMo, vmName, volumePath + ".vmdk");
            int diskId = getVirtualDiskInfo(vmMo, volumePath + ".vmdk");
            diskLocator = new VirtualMachineRelocateSpecDiskLocator();
            diskLocator.setDatastore(morDs);
            diskLocator.setDiskId(diskId);

            diskLocators.add(diskLocator);
            relocateSpec.getDisk().add(diskLocator);

            // Change datastore
            if (!vmMo.changeDatastore(relocateSpec)) {
                throw new Exception("Change datastore operation failed during volume migration");
            } else {
                s_logger.debug("Successfully migrated volume " + volumePath + " to target datastore " + tgtDsName);
            }

            // Update and return volume path because that could have changed after migration
            if (!targetDsMo.fileExists(fullVolumePath)) {
                VirtualDisk[] disks = vmMo.getAllDiskDevice();
                for (VirtualDisk disk : disks)
                    if (disk.getKey() == diskId) {
                        volumePath = vmMo.getVmdkFileBaseName(disk);
                    }
            }

            return new MigrateVolumeAnswer(cmd, true, null, volumePath);
        } catch (Exception e) {
            String msg = "Catch Exception " + e.getClass().getName() + " due to " + e.toString();
            s_logger.error(msg, e);
            return new MigrateVolumeAnswer(cmd, false, msg, null);
        }
    }

    private int getVirtualDiskInfo(VirtualMachineMO vmMo, String srcDiskName) throws Exception {
        Pair<VirtualDisk, String> deviceInfo = vmMo.getDiskDevice(srcDiskName, true);
        if (deviceInfo == null) {
            throw new Exception("No such disk device: " + srcDiskName);
        }
        return deviceInfo.first().getKey();
    }

    private VmwareHypervisorHost getTargetHyperHost(DatacenterMO dcMo, String destIp) throws Exception {

        VmwareManager mgr = dcMo.getContext().getStockObject(VmwareManager.CONTEXT_STOCK_NAME);

        List<ObjectContent> ocs = dcMo.getHostPropertiesOnDatacenterHostFolder(new String[] {"name", "parent"});
        if (ocs != null && ocs.size() > 0) {
            for (ObjectContent oc : ocs) {
                HostMO hostMo = new HostMO(dcMo.getContext(), oc.getObj());
                VmwareHypervisorHostNetworkSummary netSummary = hostMo.getHyperHostNetworkSummary(mgr.getManagementPortGroupByHost(hostMo));
                if (destIp.equalsIgnoreCase(netSummary.getHostIp())) {
                    return new HostMO(dcMo.getContext(), oc.getObj());
                }
            }
        }

        throw new Exception("Unable to locate dest host by " + destIp);
    }

    protected Answer execute(CreateStoragePoolCommand cmd) {
        if (cmd.getCreateDatastore()) {
            try {
                VmwareContext context = getServiceContext();

                _storageProcessor.prepareManagedDatastore(context, getHyperHost(context),
                        cmd.getDetails().get(CreateStoragePoolCommand.DATASTORE_NAME), cmd.getDetails().get(CreateStoragePoolCommand.IQN),
                        cmd.getDetails().get(CreateStoragePoolCommand.STORAGE_HOST), Integer.parseInt(cmd.getDetails().get(CreateStoragePoolCommand.STORAGE_PORT)));
            } catch (Exception ex) {
                return new Answer(cmd, false, "Issue creating datastore");
            }
        }

        return new Answer(cmd, true, "success");
    }

    protected Answer execute(ModifyStoragePoolCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource ModifyStoragePoolCommand: " + _gson.toJson(cmd));
        }

        try {
            VmwareHypervisorHost hyperHost = getHyperHost(getServiceContext());
            StorageFilerTO pool = cmd.getPool();

            if (pool.getType() != StoragePoolType.NetworkFilesystem && pool.getType() != StoragePoolType.VMFS) {
                throw new Exception("Unsupported storage pool type " + pool.getType());
            }

            ManagedObjectReference morDatastore = null;
            morDatastore = HypervisorHostHelper.findDatastoreWithBackwardsCompatibility(hyperHost, pool.getUuid());
            if (morDatastore == null)
                morDatastore =
                hyperHost.mountDatastore(pool.getType() == StoragePoolType.VMFS, pool.getHost(), pool.getPort(), pool.getPath(), pool.getUuid().replace("-", ""));

            assert (morDatastore != null);
            DatastoreSummary summary = new DatastoreMO(getServiceContext(), morDatastore).getSummary();
            long capacity = summary.getCapacity();
            long available = summary.getFreeSpace();
            Map<String, TemplateProp> tInfo = new HashMap<String, TemplateProp>();
            ModifyStoragePoolAnswer answer = new ModifyStoragePoolAnswer(cmd, capacity, available, tInfo);
            return answer;
        } catch (Throwable e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }

            String msg = "ModifyStoragePoolCommand failed due to " + VmwareHelper.getExceptionMessage(e);
            s_logger.error(msg, e);
            return new Answer(cmd, false, msg);
        }
    }

    protected Answer execute(DeleteStoragePoolCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource DeleteStoragePoolCommand: " + _gson.toJson(cmd));
        }

        try {
            if (cmd.getRemoveDatastore()) {
                _storageProcessor.handleDatastoreAndVmdkDetach(cmd.getDetails().get(DeleteStoragePoolCommand.DATASTORE_NAME), cmd.getDetails().get(DeleteStoragePoolCommand.IQN),
                    cmd.getDetails().get(DeleteStoragePoolCommand.STORAGE_HOST), Integer.parseInt(cmd.getDetails().get(DeleteStoragePoolCommand.STORAGE_PORT)));

                return new Answer(cmd, true, "success");
            }
            else {
                // We will leave datastore cleanup management to vCenter. Since for cluster VMFS datastore, it will always
                // be mounted by vCenter.

                // VmwareHypervisorHost hyperHost = this.getHyperHost(getServiceContext());
                // hyperHost.unmountDatastore(pool.getUuid());

                return new Answer(cmd, true, "success");
            }
        } catch (Throwable e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");

                invalidateServiceContext();
            }

            StorageFilerTO pool = cmd.getPool();
            String msg = "DeleteStoragePoolCommand (pool: " + pool.getHost() + ", path: " + pool.getPath() + ") failed due to " + VmwareHelper.getExceptionMessage(e);

            return new Answer(cmd, false, msg);
        }
    }

    public static String getDatastoreName(String str) {
        return str.replace('/', '-');
    }

    protected Answer execute(AttachIsoCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource AttachIsoCommand: " + _gson.toJson(cmd));
        }

        try {
            VmwareHypervisorHost hyperHost = getHyperHost(getServiceContext());
            VirtualMachineMO vmMo = hyperHost.findVmOnHyperHost(cmd.getVmName());
            if (vmMo == null) {
                String msg = "Unable to find VM in vSphere to execute AttachIsoCommand, vmName: " + cmd.getVmName();
                s_logger.error(msg);
                throw new Exception(msg);
            }

            String storeUrl = cmd.getStoreUrl();
            if (storeUrl == null) {
                if (!cmd.getIsoPath().equalsIgnoreCase("vmware-tools.iso")) {
                    String msg = "ISO store root url is not found in AttachIsoCommand";
                    s_logger.error(msg);
                    throw new Exception(msg);
                } else {
                    if (cmd.isAttach()) {
                        vmMo.mountToolsInstaller();
                    } else {
                        try{
                            if (!vmMo.unmountToolsInstaller()) {
                                return new Answer(cmd, false,
                                        "Failed to unmount vmware-tools installer ISO as the corresponding CDROM device is locked by VM. Please unmount the CDROM device inside the VM and ret-try.");
                            }
                        }catch(Throwable e){
                            vmMo.detachIso(null);
                        }
                    }

                    return new Answer(cmd);
                }
            }

            ManagedObjectReference morSecondaryDs = prepareSecondaryDatastoreOnHost(storeUrl);
            String isoPath = cmd.getIsoPath();
            if (!isoPath.startsWith(storeUrl)) {
                assert (false);
                String msg = "ISO path does not start with the secondary storage root";
                s_logger.error(msg);
                throw new Exception(msg);
            }

            int isoNameStartPos = isoPath.lastIndexOf('/');
            String isoFileName = isoPath.substring(isoNameStartPos + 1);
            String isoStorePathFromRoot = isoPath.substring(storeUrl.length(), isoNameStartPos);

            // TODO, check if iso is already attached, or if there is a previous
            // attachment
            DatastoreMO secondaryDsMo = new DatastoreMO(getServiceContext(), morSecondaryDs);
            String storeName = secondaryDsMo.getName();
            String isoDatastorePath = String.format("[%s] %s%s", storeName, isoStorePathFromRoot, isoFileName);

            if (cmd.isAttach()) {
                vmMo.attachIso(isoDatastorePath, morSecondaryDs, true, false);
            } else {
                vmMo.detachIso(isoDatastorePath);
            }

            return new Answer(cmd);
        } catch (Throwable e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }

            if (cmd.isAttach()) {
                String msg = "AttachIsoCommand(attach) failed due to " + VmwareHelper.getExceptionMessage(e);
                s_logger.error(msg, e);
                return new Answer(cmd, false, msg);
            } else {
                String msg = "AttachIsoCommand(detach) failed due to " + VmwareHelper.getExceptionMessage(e);
                s_logger.warn(msg, e);
                return new Answer(cmd, false, msg);
            }
        }
    }

    public synchronized ManagedObjectReference prepareSecondaryDatastoreOnHost(String storeUrl) throws Exception {
        String storeName = getSecondaryDatastoreUUID(storeUrl);
        URI uri = new URI(storeUrl);

        VmwareHypervisorHost hyperHost = getHyperHost(getServiceContext());
        ManagedObjectReference morDatastore = hyperHost.mountDatastore(false, uri.getHost(), 0, uri.getPath(), storeName.replace("-", ""));

        if (morDatastore == null)
            throw new Exception("Unable to mount secondary storage on host. storeUrl: " + storeUrl);

        return morDatastore;
    }

    private static String getSecondaryDatastoreUUID(String storeUrl) {
        return UUID.nameUUIDFromBytes(storeUrl.getBytes()).toString();
    }

    protected Answer execute(ValidateSnapshotCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource ValidateSnapshotCommand: " + _gson.toJson(cmd));
        }

        // the command is no longer available
        String expectedSnapshotBackupUuid = null;
        String actualSnapshotBackupUuid = null;
        String actualSnapshotUuid = null;
        return new ValidateSnapshotAnswer(cmd, false, "ValidateSnapshotCommand is not supported for vmware yet", expectedSnapshotBackupUuid, actualSnapshotBackupUuid,
                actualSnapshotUuid);
    }

    protected Answer execute(ManageSnapshotCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource ManageSnapshotCommand: " + _gson.toJson(cmd));
        }

        long snapshotId = cmd.getSnapshotId();

        /*
         * "ManageSnapshotCommand",
         * "{\"_commandSwitch\":\"-c\",\"_volumePath\":\"i-2-3-KY-ROOT\",\"_snapshotName\":\"i-2-3-KY_i-2-3-KY-ROOT_20101102203827\",\"_snapshotId\":1,\"_vmName\":\"i-2-3-KY\"}"
         */
        boolean success = false;
        String cmdSwitch = cmd.getCommandSwitch();
        String snapshotOp = "Unsupported snapshot command." + cmdSwitch;
        if (cmdSwitch.equals(ManageSnapshotCommand.CREATE_SNAPSHOT)) {
            snapshotOp = "create";
        } else if (cmdSwitch.equals(ManageSnapshotCommand.DESTROY_SNAPSHOT)) {
            snapshotOp = "destroy";
        }

        String details = "ManageSnapshotCommand operation: " + snapshotOp + " Failed for snapshotId: " + snapshotId;
        String snapshotUUID = null;

        // snapshot operation (create or destroy) is handled inside BackupSnapshotCommand(), we just fake
        // a success return here
        snapshotUUID = UUID.randomUUID().toString();
        success = true;
        details = null;

        return new ManageSnapshotAnswer(cmd, snapshotId, snapshotUUID, success, details);
    }

    protected Answer execute(BackupSnapshotCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource BackupSnapshotCommand: " + _gson.toJson(cmd));
        }

        try {
            VmwareContext context = getServiceContext();
            VmwareManager mgr = context.getStockObject(VmwareManager.CONTEXT_STOCK_NAME);

            return mgr.getStorageManager().execute(this, cmd);
        } catch (Throwable e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }

            String details = "BackupSnapshotCommand failed due to " + VmwareHelper.getExceptionMessage(e);
            s_logger.error(details, e);
            return new BackupSnapshotAnswer(cmd, false, details, null, true);
        }
    }

    protected Answer execute(CreateVMSnapshotCommand cmd) {
        try {
            VmwareContext context = getServiceContext();
            VmwareManager mgr = context.getStockObject(VmwareManager.CONTEXT_STOCK_NAME);

            return mgr.getStorageManager().execute(this, cmd);
        } catch (Exception e) {
            e.printStackTrace();
            return new CreateVMSnapshotAnswer(cmd, false, "");
        }
    }

    protected Answer execute(DeleteVMSnapshotCommand cmd) {
        try {
            VmwareContext context = getServiceContext();
            VmwareManager mgr = context.getStockObject(VmwareManager.CONTEXT_STOCK_NAME);

            return mgr.getStorageManager().execute(this, cmd);
        } catch (Exception e) {
            e.printStackTrace();
            return new DeleteVMSnapshotAnswer(cmd, false, "");
        }
    }

    protected Answer execute(RevertToVMSnapshotCommand cmd) {
        try {
            VmwareContext context = getServiceContext();
            VmwareManager mgr = context.getStockObject(VmwareManager.CONTEXT_STOCK_NAME);
            return mgr.getStorageManager().execute(this, cmd);
        } catch (Exception e) {
            e.printStackTrace();
            return new RevertToVMSnapshotAnswer(cmd, false, "");
        }
    }

    protected Answer execute(CreateVolumeFromSnapshotCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource CreateVolumeFromSnapshotCommand: " + _gson.toJson(cmd));
        }

        String details = null;
        boolean success = false;
        String newVolumeName = UUID.randomUUID().toString();

        try {
            VmwareContext context = getServiceContext();
            VmwareManager mgr = context.getStockObject(VmwareManager.CONTEXT_STOCK_NAME);
            return mgr.getStorageManager().execute(this, cmd);
        } catch (Throwable e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }

            details = "CreateVolumeFromSnapshotCommand failed due to " + VmwareHelper.getExceptionMessage(e);
            s_logger.error(details, e);
        }

        return new CreateVolumeFromSnapshotAnswer(cmd, success, details, newVolumeName);
    }

    protected Answer execute(CreatePrivateTemplateFromVolumeCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource CreatePrivateTemplateFromVolumeCommand: " + _gson.toJson(cmd));
        }

        try {
            VmwareContext context = getServiceContext();
            VmwareManager mgr = context.getStockObject(VmwareManager.CONTEXT_STOCK_NAME);

            return mgr.getStorageManager().execute(this, cmd);

        } catch (Throwable e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }

            String details = "CreatePrivateTemplateFromVolumeCommand failed due to " + VmwareHelper.getExceptionMessage(e);
            s_logger.error(details, e);
            return new CreatePrivateTemplateAnswer(cmd, false, details);
        }
    }

    protected Answer execute(final UpgradeSnapshotCommand cmd) {
        return new Answer(cmd, true, "success");
    }

    protected Answer execute(CreatePrivateTemplateFromSnapshotCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource CreatePrivateTemplateFromSnapshotCommand: " + _gson.toJson(cmd));
        }

        try {
            VmwareManager mgr = getServiceContext().getStockObject(VmwareManager.CONTEXT_STOCK_NAME);
            return mgr.getStorageManager().execute(this, cmd);

        } catch (Throwable e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }

            String details = "CreatePrivateTemplateFromSnapshotCommand failed due to " + VmwareHelper.getExceptionMessage(e);
            s_logger.error(details, e);
            return new CreatePrivateTemplateAnswer(cmd, false, details);
        }
    }

    protected Answer execute(GetStorageStatsCommand cmd) {
        if (s_logger.isTraceEnabled()) {
            s_logger.trace("Executing resource GetStorageStatsCommand: " + _gson.toJson(cmd));
        }

        try {
            VmwareContext context = getServiceContext();
            VmwareHypervisorHost hyperHost = getHyperHost(context);
            ManagedObjectReference morDs = HypervisorHostHelper.findDatastoreWithBackwardsCompatibility(hyperHost, cmd.getStorageId());

            if (morDs != null) {
                DatastoreMO datastoreMo = new DatastoreMO(context, morDs);
                DatastoreSummary summary = datastoreMo.getSummary();
                assert (summary != null);

                long capacity = summary.getCapacity();
                long free = summary.getFreeSpace();
                long used = capacity - free;

                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Datastore summary info, storageId: " + cmd.getStorageId() + ", localPath: " + cmd.getLocalPath() + ", poolType: " +
                            cmd.getPooltype() + ", capacity: " + capacity + ", free: " + free + ", used: " + used);
                }

                if (summary.getCapacity() <= 0) {
                    s_logger.warn("Something is wrong with vSphere NFS datastore, rebooting ESX(ESXi) host should help");
                }

                return new GetStorageStatsAnswer(cmd, capacity, used);
            } else {
                String msg =
                        "Could not find datastore for GetStorageStatsCommand storageId : " + cmd.getStorageId() + ", localPath: " + cmd.getLocalPath() + ", poolType: " +
                                cmd.getPooltype();

                s_logger.error(msg);
                return new GetStorageStatsAnswer(cmd, msg);
            }
        } catch (Throwable e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }

            String msg =
                    "Unable to execute GetStorageStatsCommand(storageId : " + cmd.getStorageId() + ", localPath: " + cmd.getLocalPath() + ", poolType: " + cmd.getPooltype() +
                    ") due to " + VmwareHelper.getExceptionMessage(e);
            s_logger.error(msg, e);
            return new GetStorageStatsAnswer(cmd, msg);
        }
    }

    protected Answer execute(GetVncPortCommand cmd) {
        if (s_logger.isTraceEnabled()) {
            s_logger.trace("Executing resource GetVncPortCommand: " + _gson.toJson(cmd));
        }

        try {
            VmwareContext context = getServiceContext();
            VmwareHypervisorHost hyperHost = getHyperHost(context);
            assert (hyperHost instanceof HostMO);
            VmwareManager mgr = context.getStockObject(VmwareManager.CONTEXT_STOCK_NAME);

            VirtualMachineMO vmMo = hyperHost.findVmOnHyperHost(cmd.getName());
            if (vmMo == null) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Unable to find the owner VM for GetVncPortCommand on host " + hyperHost.getHyperHostName() + ", try within datacenter");
                }

                vmMo = hyperHost.findVmOnPeerHyperHost(cmd.getName());

                if (vmMo == null) {
                    throw new Exception("Unable to find VM in vSphere, vm: " + cmd.getName());
                }
            }

            Pair<String, Integer> portInfo = vmMo.getVncPort(mgr.getManagementPortGroupByHost((HostMO)hyperHost));

            if (s_logger.isTraceEnabled()) {
                s_logger.trace("Found vnc port info. vm: " + cmd.getName() + " host: " + portInfo.first() + ", vnc port: " + portInfo.second());
            }
            return new GetVncPortAnswer(cmd, portInfo.first(), portInfo.second());
        } catch (Throwable e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }

            String msg = "GetVncPortCommand failed due to " + VmwareHelper.getExceptionMessage(e);
            s_logger.error(msg, e);
            return new GetVncPortAnswer(cmd, msg);
        }
    }

    protected Answer execute(SetupCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource SetupCommand: " + _gson.toJson(cmd));
        }

        return new SetupAnswer(cmd, false);
    }

    protected Answer execute(MaintainCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource MaintainCommand: " + _gson.toJson(cmd));
        }

        return new MaintainAnswer(cmd, "Put host in maintaince");
    }

    protected Answer execute(PingTestCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource PingTestCommand: " + _gson.toJson(cmd));
        }

        String controlIp = cmd.getRouterIp();
        if (controlIp != null) {
            String args = " -c 1 -n -q " + cmd.getPrivateIp();
            try {
                VmwareManager mgr = getServiceContext().getStockObject(VmwareManager.CONTEXT_STOCK_NAME);
                Pair<Boolean, String> result = SshHelper.sshExecute(controlIp, DefaultDomRSshPort, "root", mgr.getSystemVMKeyFile(), null, "/bin/ping" + args);
                if (result.first())
                    return new Answer(cmd);
            } catch (Exception e) {
                s_logger.error("Unable to execute ping command on DomR (" + controlIp + "), domR may not be ready yet. failure due to "
                        + VmwareHelper.getExceptionMessage(e), e);
            }
            return new Answer(cmd, false, "PingTestCommand failed");
        } else {
            VmwareContext context = getServiceContext();
            VmwareHypervisorHost hyperHost = getHyperHost(context);

            try {
                HostMO hostMo = (HostMO)hyperHost;
                ClusterMO clusterMo = new ClusterMO(context, hostMo.getHyperHostCluster());
                VmwareManager mgr = context.getStockObject(VmwareManager.CONTEXT_STOCK_NAME);

                List<Pair<ManagedObjectReference, String>> hosts = clusterMo.getClusterHosts();
                for (Pair<ManagedObjectReference, String> entry : hosts) {
                    HostMO hostInCluster = new HostMO(context, entry.first());
                    String hostIp = hostInCluster.getHostManagementIp(mgr.getManagementPortGroupName());
                    if (hostIp != null && hostIp.equals(cmd.getComputingHostIp())) {
                        if (hostInCluster.isHyperHostConnected())
                            return new Answer(cmd);
                        else
                            new Answer(cmd, false, "PingTestCommand failed");
                    }
                }
            } catch (Exception e) {
                s_logger.error("Unable to execute ping command on host (" + cmd.getComputingHostIp() + "). failure due to "
                        + VmwareHelper.getExceptionMessage(e), e);
            }

            return new Answer(cmd, false, "PingTestCommand failed");
        }
    }

    protected Answer execute(CheckOnHostCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource CheckOnHostCommand: " + _gson.toJson(cmd));
        }

        return new CheckOnHostAnswer(cmd, null, "Not Implmeneted");
    }

    protected Answer execute(ModifySshKeysCommand cmd) {
        //do not log the command contents for this command. do NOT log the ssh keys
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource ModifySshKeysCommand.");
        }

        return new Answer(cmd);
    }


    @Override
    public PrimaryStorageDownloadAnswer execute(PrimaryStorageDownloadCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource PrimaryStorageDownloadCommand: " + _gson.toJson(cmd));
        }

        try {
            VmwareContext context = getServiceContext();
            VmwareManager mgr = context.getStockObject(VmwareManager.CONTEXT_STOCK_NAME);
            return (PrimaryStorageDownloadAnswer)mgr.getStorageManager().execute(this, cmd);
        } catch (Throwable e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }

            String msg = "PrimaryStorageDownloadCommand failed due to " + VmwareHelper.getExceptionMessage(e);
            s_logger.error(msg, e);
            return new PrimaryStorageDownloadAnswer(msg);
        }
    }

    protected Answer execute(PvlanSetupCommand cmd) {
        // Pvlan related operations are performed in the start/stop command paths
        // for vmware. This function is implemented to support mgmt layer code
        // that issue this command. Note that pvlan operations are supported only
        // in Distributed Virtual Switch environments for vmware deployments.
        return new Answer(cmd, true, "success");
    }

    protected Answer execute(UnregisterVMCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource UnregisterVMCommand: " + _gson.toJson(cmd));
        }

        VmwareContext context = getServiceContext();
        VmwareHypervisorHost hyperHost = getHyperHost(context);
        try {
            VirtualMachineMO vmMo = hyperHost.findVmOnHyperHost(cmd.getVmName());
            if (vmMo != null) {
                try {
                    context.getService().unregisterVM(vmMo.getMor());
                    return new Answer(cmd, true, "unregister succeeded");
                } catch (Exception e) {
                    s_logger.warn("We are not able to unregister VM " + VmwareHelper.getExceptionMessage(e));
                }

                String msg = "Expunge failed in vSphere. vm: " + cmd.getVmName();
                s_logger.warn(msg);
                return new Answer(cmd, false, msg);
            } else {
                String msg = "Unable to find the VM in vSphere to unregister, assume it is already removed. VM: " + cmd.getVmName();
                s_logger.warn(msg);
                return new Answer(cmd, true, msg);
            }
        } catch (Exception e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }

            String msg = "UnregisterVMCommand failed due to " + VmwareHelper.getExceptionMessage(e);
            s_logger.error(msg);
            return new Answer(cmd, false, msg);
        }
    }

    /**
     * UnregisterNicCommand is used to remove a portgroup created for this
     * specific nic. The portgroup will have the name set to the UUID of the
     * nic. Introduced to cleanup the portgroups created for each nic that is
     * plugged into an lswitch (Nicira NVP plugin)
     *
     * @param cmd
     * @return
     */
    protected Answer execute(UnregisterNicCommand cmd) {
        s_logger.info("Executing resource UnregisterNicCommand: " + _gson.toJson(cmd));

        if (_guestTrafficInfo == null) {
            return new Answer(cmd, false, "No Guest Traffic Info found, unable to determine where to clean up");
        }

        try {
            if (_guestTrafficInfo.getVirtualSwitchType() != VirtualSwitchType.StandardVirtualSwitch) {
                // For now we only need to cleanup the nvp specific portgroups
                // on the standard switches
                return new Answer(cmd, true, "Nothing to do");
            }

            s_logger.debug("Cleaning up portgroup " + cmd.getNicUuid() + " on switch " + _guestTrafficInfo.getVirtualSwitchName());
            VmwareContext context = getServiceContext();
            VmwareHypervisorHost host = getHyperHost(context);
            ManagedObjectReference clusterMO = host.getHyperHostCluster();

            // Get a list of all the hosts in this cluster
            @SuppressWarnings("unchecked")
            List<ManagedObjectReference> hosts = (List<ManagedObjectReference>)context.getVimClient().getDynamicProperty(clusterMO, "host");
            if (hosts == null) {
                return new Answer(cmd, false, "No hosts in cluster, which is pretty weird");
            }

            for (ManagedObjectReference hostMOR : hosts) {
                HostMO hostMo = new HostMO(context, hostMOR);
                hostMo.deletePortGroup(cmd.getNicUuid().toString());
                s_logger.debug("Removed portgroup " + cmd.getNicUuid() + " from host " + hostMo.getHostName());
            }
            return new Answer(cmd, true, "Unregistered resources for NIC " + cmd.getNicUuid());
        } catch (Exception e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }

            String msg = "UnregisterVMCommand failed due to " + VmwareHelper.getExceptionMessage(e);
            s_logger.error(msg);
            return new Answer(cmd, false, msg);
        }
    }

    public void cleanupNetwork(HostMO hostMo, NetworkDetails netDetails) {
        // we will no longer cleanup VLAN networks in order to support native VMware HA
        /*
         * assert(netDetails.getName() != null); try { synchronized(this) { NetworkMO networkMo = new
         * NetworkMO(hostMo.getContext(), netDetails.getNetworkMor()); ManagedObjectReference[] vms =
         * networkMo.getVMsOnNetwork(); if(vms == null || vms.length == 0) { if(s_logger.isInfoEnabled()) {
         * s_logger.info("Cleanup network as it is currently not in use: " + netDetails.getName()); }
         *
         * hostMo.deletePortGroup(netDetails.getName()); } } } catch(Throwable e) {
         * s_logger.warn("Unable to cleanup network due to exception, skip for next time"); }
         */
    }

    @Override
    public CopyVolumeAnswer execute(CopyVolumeCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource CopyVolumeCommand: " + _gson.toJson(cmd));
        }

        try {
            VmwareContext context = getServiceContext();
            VmwareManager mgr = context.getStockObject(VmwareManager.CONTEXT_STOCK_NAME);
            return (CopyVolumeAnswer)mgr.getStorageManager().execute(this, cmd);
        } catch (Throwable e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }

            String msg = "CopyVolumeCommand failed due to " + VmwareHelper.getExceptionMessage(e);
            s_logger.error(msg, e);
            return new CopyVolumeAnswer(cmd, false, msg, null, null);
        }
    }

    @Override
    public void disconnected() {
    }

    @Override
    public IAgentControl getAgentControl() {
        return null;
    }

    @Override
    public PingCommand getCurrentStatus(long id) {
        try {
            gcAndKillHungWorkerVMs();
            VmwareContext context = getServiceContext();
            VmwareHypervisorHost hyperHost = getHyperHost(context);
            try {
                if (!hyperHost.isHyperHostConnected()) {
                    return null;
                }
            } catch (Exception e) {
                s_logger.error("Unexpected exception", e);
                return null;
            }
            return new PingRoutingCommand(getType(), id, syncHostVmStates());
        } finally {
            recycleServiceContext();
        }
    }

    private void gcAndKillHungWorkerVMs() {
        try {
            // take the chance to do left-over dummy VM cleanup from previous run
            VmwareContext context = getServiceContext();
            VmwareHypervisorHost hyperHost = getHyperHost(context);
            VmwareManager mgr = hyperHost.getContext().getStockObject(VmwareManager.CONTEXT_STOCK_NAME);

            if (hyperHost.isHyperHostConnected()) {
                mgr.gcLeftOverVMs(context);

                s_logger.info("Scan hung worker VM to recycle");

                int workerKey = ((HostMO)hyperHost).getCustomFieldKey("VirtualMachine", CustomFieldConstants.CLOUD_WORKER);
                int workerTagKey = ((HostMO)hyperHost).getCustomFieldKey("VirtualMachine", CustomFieldConstants.CLOUD_WORKER_TAG);
                String workerPropName = String.format("value[%d]", workerKey);
                String workerTagPropName = String.format("value[%d]", workerTagKey);

                // GC worker that has been running for too long
                ObjectContent[] ocs = hyperHost.getVmPropertiesOnHyperHost(new String[] {"name", "config.template", workerPropName, workerTagPropName,});
                if (ocs != null) {
                    for (ObjectContent oc : ocs) {
                        List<DynamicProperty> props = oc.getPropSet();
                        if (props != null) {
                            boolean template = false;
                            boolean isWorker = false;
                            String workerTag = null;

                            for (DynamicProperty prop : props) {
                                if (prop.getName().equals("config.template")) {
                                    template = (Boolean)prop.getVal();
                                } else if (prop.getName().equals(workerPropName)) {
                                    CustomFieldStringValue val = (CustomFieldStringValue)prop.getVal();
                                    if (val != null && val.getValue() != null && val.getValue().equalsIgnoreCase("true"))
                                        isWorker = true;
                                } else if (prop.getName().equals(workerTagPropName)) {
                                    CustomFieldStringValue val = (CustomFieldStringValue)prop.getVal();
                                    workerTag = val.getValue();
                                }
                            }

                            VirtualMachineMO vmMo = new VirtualMachineMO(hyperHost.getContext(), oc.getObj());
                            if (!template && isWorker) {
                                boolean recycle = false;
                                recycle = mgr.needRecycle(workerTag);

                                if (recycle) {
                                    s_logger.info("Recycle pending worker VM: " + vmMo.getName());

                                    vmMo.powerOff();
                                    vmMo.detachAllDisks();
                                    vmMo.destroy();
                                }
                            }
                        }
                    }
                }
            } else {
                s_logger.error("Host is no longer connected.");
            }

        } catch (Throwable e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext();
            }
        }
    }

    @Override
    public Type getType() {
        return com.cloud.host.Host.Type.Routing;
    }

    @Override
    public StartupCommand[] initialize() {
        try {
            String hostApiVersion = "4.1";
            VmwareContext context = getServiceContext();
            try {
                VmwareHypervisorHost hyperHost = getHyperHost(context);
                assert (hyperHost instanceof HostMO);
                if (!((HostMO)hyperHost).isHyperHostConnected()) {
                    s_logger.info("Host " + hyperHost.getHyperHostName() + " is not in connected state");
                    return null;
                }

                ((HostMO)hyperHost).enableVncOnHostFirewall();

                AboutInfo aboutInfo = ((HostMO)hyperHost).getHostAboutInfo();
                hostApiVersion = aboutInfo.getApiVersion();

            } catch (Exception e) {
                String msg = "VmwareResource intialize() failed due to : " + VmwareHelper.getExceptionMessage(e);
                s_logger.error(msg);
                invalidateServiceContext();
                return null;
            }

            StartupRoutingCommand cmd = new StartupRoutingCommand();
            fillHostInfo(cmd);
            cmd.setHypervisorType(HypervisorType.VMware);
            cmd.setCluster(_cluster);
            cmd.setHypervisorVersion(hostApiVersion);

            List<StartupStorageCommand> storageCmds = initializeLocalStorage();
            StartupCommand[] answerCmds = new StartupCommand[1 + storageCmds.size()];
            answerCmds[0] = cmd;
            for (int i = 0; i < storageCmds.size(); i++) {
                answerCmds[i + 1] = storageCmds.get(i);
            }

            return answerCmds;
        } finally {
            recycleServiceContext();
        }
    }

    private List<StartupStorageCommand> initializeLocalStorage() {
        List<StartupStorageCommand> storageCmds = new ArrayList<StartupStorageCommand>();
        VmwareContext context = getServiceContext();

        try {
            VmwareHypervisorHost hyperHost = getHyperHost(context);
            if (hyperHost instanceof HostMO) {
                HostMO hostMo = (HostMO)hyperHost;

                List<Pair<ManagedObjectReference, String>> dsList = hostMo.getLocalDatastoreOnHost();
                for (Pair<ManagedObjectReference, String> dsPair : dsList) {
                    DatastoreMO dsMo = new DatastoreMO(context, dsPair.first());

                    String poolUuid = dsMo.getCustomFieldValue(CustomFieldConstants.CLOUD_UUID);
                    if (poolUuid == null || poolUuid.isEmpty()) {
                        poolUuid = UUID.randomUUID().toString();
                        dsMo.setCustomFieldValue(CustomFieldConstants.CLOUD_UUID, poolUuid);
                    }

                    DatastoreSummary dsSummary = dsMo.getSummary();
                    String address = hostMo.getHostName();
                    StoragePoolInfo pInfo =
                            new StoragePoolInfo(poolUuid, address, dsMo.getMor().getValue(), "", StoragePoolType.LVM, dsSummary.getCapacity(), dsSummary.getFreeSpace());
                    StartupStorageCommand cmd = new StartupStorageCommand();
                    cmd.setName(poolUuid);
                    cmd.setPoolInfo(pInfo);
                    cmd.setGuid(poolUuid); // give storage host the same UUID as the local storage pool itself
                    cmd.setResourceType(Storage.StorageResourceType.STORAGE_POOL);
                    cmd.setDataCenter(_dcId);
                    cmd.setPod(_pod);
                    cmd.setCluster(_cluster);

                    s_logger.info("Add local storage startup command: " + _gson.toJson(cmd));
                    storageCmds.add(cmd);
                }

            } else {
                s_logger.info("Cluster host does not support local storage, skip it");
            }
        } catch (Exception e) {
            String msg = "initializing local storage failed due to : " + VmwareHelper.getExceptionMessage(e);
            s_logger.error(msg);
            invalidateServiceContext();
            throw new CloudRuntimeException(msg);
        }

        return storageCmds;
    }

    protected void fillHostInfo(StartupRoutingCommand cmd) {
        VmwareContext serviceContext = getServiceContext();
        Map<String, String> details = cmd.getHostDetails();
        if (details == null) {
            details = new HashMap<String, String>();
        }

        try {
            fillHostHardwareInfo(serviceContext, cmd);
            fillHostNetworkInfo(serviceContext, cmd);
            fillHostDetailsInfo(serviceContext, details);
        } catch (RuntimeFaultFaultMsg e) {
            s_logger.error("RuntimeFault while retrieving host info: " + e.toString(), e);
            throw new CloudRuntimeException("RuntimeFault while retrieving host info");
        } catch (RemoteException e) {
            s_logger.error("RemoteException while retrieving host info: " + e.toString(), e);
            invalidateServiceContext();
            throw new CloudRuntimeException("RemoteException while retrieving host info");
        } catch (Exception e) {
            s_logger.error("Exception while retrieving host info: " + e.toString(), e);
            invalidateServiceContext();
            throw new CloudRuntimeException("Exception while retrieving host info: " + e.toString());
        }

        cmd.setHostDetails(details);
        cmd.setName(_url);
        cmd.setGuid(_guid);
        cmd.setDataCenter(_dcId);
        cmd.setIqn(getIqn());
        cmd.setPod(_pod);
        cmd.setCluster(_cluster);
        cmd.setVersion(VmwareResource.class.getPackage().getImplementationVersion());
    }

    private String getIqn() {
        try {
            VmwareHypervisorHost hyperHost = getHyperHost(getServiceContext());

            if (hyperHost instanceof HostMO) {
                HostMO host = (HostMO)hyperHost;
                HostStorageSystemMO hostStorageSystem = host.getHostStorageSystemMO();

                for (HostHostBusAdapter hba : hostStorageSystem.getStorageDeviceInfo().getHostBusAdapter()) {
                    if (hba instanceof HostInternetScsiHba) {
                        return ((HostInternetScsiHba)hba).getIScsiName();
                    }
                }
            }
        }
        catch (Exception ex) {
            s_logger.info("Could not locate an IQN for this host.");
        }

        return null;
    }

    private void fillHostHardwareInfo(VmwareContext serviceContext, StartupRoutingCommand cmd) throws RuntimeFaultFaultMsg, RemoteException, Exception {

        VmwareHypervisorHost hyperHost = getHyperHost(getServiceContext());
        VmwareHypervisorHostResourceSummary summary = hyperHost.getHyperHostResourceSummary();

        if (s_logger.isInfoEnabled()) {
            s_logger.info("Startup report on host hardware info. " + _gson.toJson(summary));
        }

        cmd.setCaps("hvm");
        cmd.setDom0MinMemory(0);
        cmd.setSpeed(summary.getCpuSpeed());
        cmd.setCpuSockets(summary.getCpuSockets());
        cmd.setCpus((int)summary.getCpuCount());
        cmd.setMemory(summary.getMemoryBytes());
    }

    private void fillHostNetworkInfo(VmwareContext serviceContext, StartupRoutingCommand cmd) throws RuntimeFaultFaultMsg, RemoteException {

        try {
            VmwareHypervisorHost hyperHost = getHyperHost(getServiceContext());

            assert (hyperHost instanceof HostMO);
            VmwareManager mgr = hyperHost.getContext().getStockObject(VmwareManager.CONTEXT_STOCK_NAME);

            VmwareHypervisorHostNetworkSummary summary = hyperHost.getHyperHostNetworkSummary(mgr.getManagementPortGroupByHost((HostMO)hyperHost));
            if (summary == null) {
                throw new Exception("No ESX(i) host found");
            }

            if (s_logger.isInfoEnabled()) {
                s_logger.info("Startup report on host network info. " + _gson.toJson(summary));
            }

            cmd.setPrivateIpAddress(summary.getHostIp());
            cmd.setPrivateNetmask(summary.getHostNetmask());
            cmd.setPrivateMacAddress(summary.getHostMacAddress());

            cmd.setStorageIpAddress(summary.getHostIp());
            cmd.setStorageNetmask(summary.getHostNetmask());
            cmd.setStorageMacAddress(summary.getHostMacAddress());

        } catch (Throwable e) {
            String msg = "querying host network info failed due to " + VmwareHelper.getExceptionMessage(e);
            s_logger.error(msg, e);
            throw new CloudRuntimeException(msg);
        }
    }

    private void fillHostDetailsInfo(VmwareContext serviceContext, Map<String, String> details) throws Exception {
        VmwareHypervisorHost hyperHost = getHyperHost(getServiceContext());

        ClusterDasConfigInfo dasConfig = hyperHost.getDasConfig();
        if (dasConfig != null && dasConfig.isEnabled() != null && dasConfig.isEnabled().booleanValue()) {
            details.put("NativeHA", "true");
        }
    }

    protected HashMap<String, HostVmStateReportEntry> syncHostVmStates() {
        try {
            return getHostVmStateReport();
        } catch (Exception e) {
            return new HashMap<String, HostVmStateReportEntry>();
        }
    }

    private boolean isVmInCluster(String vmName) throws Exception {
        VmwareHypervisorHost hyperHost = getHyperHost(getServiceContext());

        return hyperHost.findVmOnPeerHyperHost(vmName) != null;
    }

    protected OptionValue[] configureVnc(OptionValue[] optionsToMerge, VmwareHypervisorHost hyperHost, String vmName, String vncPassword, String keyboardLayout)
            throws Exception {

        VirtualMachineMO vmMo = hyperHost.findVmOnHyperHost(vmName);

        VmwareManager mgr = hyperHost.getContext().getStockObject(VmwareManager.CONTEXT_STOCK_NAME);
        if (!mgr.beginExclusiveOperation(600))
            throw new Exception("Unable to begin exclusive operation, lock time out");

        try {
            int maxVncPorts = 64;
            int vncPort = 0;
            Random random = new Random();

            HostMO vmOwnerHost = vmMo.getRunningHost();

            ManagedObjectReference morParent = vmOwnerHost.getParentMor();
            HashMap<String, Integer> portInfo;
            if (morParent.getType().equalsIgnoreCase("ClusterComputeResource")) {
                ClusterMO clusterMo = new ClusterMO(vmOwnerHost.getContext(), morParent);
                portInfo = clusterMo.getVmVncPortsOnCluster();
            } else {
                portInfo = vmOwnerHost.getVmVncPortsOnHost();
            }

            // allocate first at 5900 - 5964 range
            Collection<Integer> existingPorts = portInfo.values();
            int val = random.nextInt(maxVncPorts);
            int startVal = val;
            do {
                if (!existingPorts.contains(5900 + val)) {
                    vncPort = 5900 + val;
                    break;
                }

                val = (++val) % maxVncPorts;
            } while (val != startVal);

            if (vncPort == 0) {
                s_logger.info("we've run out of range for ports between 5900-5964 for the cluster, we will try port range at 59000-60000");

                Pair<Integer, Integer> additionalRange = mgr.getAddiionalVncPortRange();
                maxVncPorts = additionalRange.second();
                val = random.nextInt(maxVncPorts);
                startVal = val;
                do {
                    if (!existingPorts.contains(additionalRange.first() + val)) {
                        vncPort = additionalRange.first() + val;
                        break;
                    }

                    val = (++val) % maxVncPorts;
                } while (val != startVal);
            }

            if (vncPort == 0) {
                throw new Exception("Unable to find an available VNC port on host");
            }

            if (s_logger.isInfoEnabled()) {
                s_logger.info("Configure VNC port for VM " + vmName + ", port: " + vncPort + ", host: " + vmOwnerHost.getHyperHostName());
            }

            return VmwareHelper.composeVncOptions(optionsToMerge, true, vncPassword, vncPort, keyboardLayout);
        } finally {
            try {
                mgr.endExclusiveOperation();
            } catch (Throwable e) {
                assert (false);
                s_logger.error("Unexpected exception ", e);
            }
        }
    }

    private VirtualMachineGuestOsIdentifier translateGuestOsIdentifier(String cpuArchitecture, String guestOs, String cloudGuestOs) {
        if (cpuArchitecture == null) {
            s_logger.warn("CPU arch is not set, default to i386. guest os: " + guestOs);
            cpuArchitecture = "i386";
        }

        if(cloudGuestOs == null) {
            s_logger.warn("Guest OS mapping name is not set for guest os: " + guestOs);
        }

        VirtualMachineGuestOsIdentifier identifier = null;
        try {
            if (cloudGuestOs != null) {
                identifier = VirtualMachineGuestOsIdentifier.fromValue(cloudGuestOs);
                s_logger.debug("Using mapping name : " + identifier.toString());
            }
        } catch (IllegalArgumentException e) {
            s_logger.warn("Unable to find Guest OS Identifier in VMware for mapping name: " + cloudGuestOs + ". Continuing with defaults.");
        }
        if (identifier != null) {
            return identifier;
        }

        if (cpuArchitecture.equalsIgnoreCase("x86_64")) {
            return VirtualMachineGuestOsIdentifier.OTHER_GUEST_64;
        }
        return VirtualMachineGuestOsIdentifier.OTHER_GUEST;
    }

    private HashMap<String, HostVmStateReportEntry> getHostVmStateReport() throws Exception {
        VmwareHypervisorHost hyperHost = getHyperHost(getServiceContext());

        int key = ((HostMO)hyperHost).getCustomFieldKey("VirtualMachine", CustomFieldConstants.CLOUD_VM_INTERNAL_NAME);
        if (key == 0) {
            s_logger.warn("Custom field " + CustomFieldConstants.CLOUD_VM_INTERNAL_NAME + " is not registered ?!");
        }
        String instanceNameCustomField = "value[" + key + "]";

        // CLOUD_VM_INTERNAL_NAME stores the internal CS generated vm name. This was earlier stored in name. Now, name can be either the hostname or
        // the internal CS name, but the custom field CLOUD_VM_INTERNAL_NAME always stores the internal CS name.
        ObjectContent[] ocs = hyperHost.getVmPropertiesOnHyperHost(new String[] {"name", "runtime.powerState", "config.template", instanceNameCustomField});

        HashMap<String, HostVmStateReportEntry> newStates = new HashMap<String, HostVmStateReportEntry>();
        if (ocs != null && ocs.length > 0) {
            for (ObjectContent oc : ocs) {
                List<DynamicProperty> objProps = oc.getPropSet();
                if (objProps != null) {

                    boolean isTemplate = false;
                    String name = null;
                    String VMInternalCSName = null;
                    VirtualMachinePowerState powerState = VirtualMachinePowerState.POWERED_OFF;
                    for (DynamicProperty objProp : objProps) {
                        if (objProp.getName().equals("config.template")) {
                            if (objProp.getVal().toString().equalsIgnoreCase("true")) {
                                isTemplate = true;
                            }
                        } else if (objProp.getName().equals("runtime.powerState")) {
                            powerState = (VirtualMachinePowerState)objProp.getVal();
                        } else if (objProp.getName().equals("name")) {
                            name = (String)objProp.getVal();
                        } else if (objProp.getName().contains(instanceNameCustomField)) {
                            if (objProp.getVal() != null)
                                VMInternalCSName = ((CustomFieldStringValue)objProp.getVal()).getValue();
                        } else {
                            assert (false);
                        }
                    }

                    if (VMInternalCSName != null)
                        name = VMInternalCSName;

                    if (!isTemplate) {
                        newStates.put(name, new HostVmStateReportEntry(convertPowerState(powerState), hyperHost.getHyperHostName()));
                    }
                }
            }
        }
        return newStates;
    }


    private HashMap<String, PowerState> getVmStates() throws Exception {
        VmwareHypervisorHost hyperHost = getHyperHost(getServiceContext());

        int key = ((HostMO)hyperHost).getCustomFieldKey("VirtualMachine", CustomFieldConstants.CLOUD_VM_INTERNAL_NAME);
        if (key == 0) {
            s_logger.warn("Custom field " + CustomFieldConstants.CLOUD_VM_INTERNAL_NAME + " is not registered ?!");
        }
        String instanceNameCustomField = "value[" + key + "]";

        // CLOUD_VM_INTERNAL_NAME stores the internal CS generated vm name. This was earlier stored in name. Now, name can be either the hostname or
        // the internal CS name, but the custom field CLOUD_VM_INTERNAL_NAME always stores the internal CS name.
        ObjectContent[] ocs = hyperHost.getVmPropertiesOnHyperHost(new String[] {"name", "runtime.powerState", "config.template", instanceNameCustomField});

        HashMap<String, PowerState> newStates = new HashMap<String, PowerState>();
        if (ocs != null && ocs.length > 0) {
            for (ObjectContent oc : ocs) {
                List<DynamicProperty> objProps = oc.getPropSet();
                if (objProps != null) {

                    boolean isTemplate = false;
                    String name = null;
                    String VMInternalCSName = null;
                    VirtualMachinePowerState powerState = VirtualMachinePowerState.POWERED_OFF;
                    for (DynamicProperty objProp : objProps) {
                        if (objProp.getName().equals("config.template")) {
                            if (objProp.getVal().toString().equalsIgnoreCase("true")) {
                                isTemplate = true;
                            }
                        } else if (objProp.getName().equals("runtime.powerState")) {
                            powerState = (VirtualMachinePowerState)objProp.getVal();
                        } else if (objProp.getName().equals("name")) {
                            name = (String)objProp.getVal();
                        } else if (objProp.getName().contains(instanceNameCustomField)) {
                            if (objProp.getVal() != null)
                                VMInternalCSName = ((CustomFieldStringValue)objProp.getVal()).getValue();
                        } else {
                            assert (false);
                        }
                    }

                    if (VMInternalCSName != null)
                        name = VMInternalCSName;

                    if (!isTemplate) {
                        newStates.put(name, convertPowerState(powerState));
                    }
                }
            }
        }
        return newStates;
    }



    private HashMap<String, VmStatsEntry> getVmStats(List<String> vmNames) throws Exception {
        VmwareHypervisorHost hyperHost = getHyperHost(getServiceContext());
        HashMap<String, VmStatsEntry> vmResponseMap = new HashMap<String, VmStatsEntry>();
        ManagedObjectReference perfMgr = getServiceContext().getServiceContent().getPerfManager();
        VimPortType service = getServiceContext().getService();
        PerfCounterInfo rxPerfCounterInfo = null;
        PerfCounterInfo txPerfCounterInfo = null;

        List<PerfCounterInfo> cInfo = getServiceContext().getVimClient().getDynamicProperty(perfMgr, "perfCounter");
        for (PerfCounterInfo info : cInfo) {
            if ("net".equalsIgnoreCase(info.getGroupInfo().getKey())) {
                if ("transmitted".equalsIgnoreCase(info.getNameInfo().getKey())) {
                    txPerfCounterInfo = info;
                }
                if ("received".equalsIgnoreCase(info.getNameInfo().getKey())) {
                    rxPerfCounterInfo = info;
                }
            }
        }

        int key = ((HostMO)hyperHost).getCustomFieldKey("VirtualMachine", CustomFieldConstants.CLOUD_VM_INTERNAL_NAME);
        if (key == 0) {
            s_logger.warn("Custom field " + CustomFieldConstants.CLOUD_VM_INTERNAL_NAME + " is not registered ?!");
        }
        String instanceNameCustomField = "value[" + key + "]";

        ObjectContent[] ocs =
                hyperHost.getVmPropertiesOnHyperHost(new String[] {"name", "summary.config.numCpu", "summary.quickStats.overallCpuUsage", instanceNameCustomField});
        if (ocs != null && ocs.length > 0) {
            for (ObjectContent oc : ocs) {
                List<DynamicProperty> objProps = oc.getPropSet();
                if (objProps != null) {
                    String name = null;
                    String numberCPUs = null;
                    String maxCpuUsage = null;
                    String vmNameOnVcenter = null;
                    String vmInternalCSName = null;
                    for (DynamicProperty objProp : objProps) {
                        if (objProp.getName().equals("name")) {
                            vmNameOnVcenter = objProp.getVal().toString();
                        } else if (objProp.getName().contains(instanceNameCustomField)) {
                            if (objProp.getVal() != null)
                                vmInternalCSName = ((CustomFieldStringValue)objProp.getVal()).getValue();
                        } else if (objProp.getName().equals("summary.config.numCpu")) {
                            numberCPUs = objProp.getVal().toString();
                        } else if (objProp.getName().equals("summary.quickStats.overallCpuUsage")) {
                            maxCpuUsage = objProp.getVal().toString();
                        }
                    }
                    new VirtualMachineMO(hyperHost.getContext(), oc.getObj());
                    if (vmInternalCSName != null) {
                        name = vmInternalCSName;
                    } else {
                        name = vmNameOnVcenter;
                    }

                    if (!vmNames.contains(name)) {
                        continue;
                    }

                    ManagedObjectReference vmMor = hyperHost.findVmOnHyperHost(name).getMor();
                    assert (vmMor != null);

                    ArrayList<PerfMetricId> vmNetworkMetrics = new ArrayList<PerfMetricId>();
                    // get all the metrics from the available sample period
                    List<PerfMetricId> perfMetrics = service.queryAvailablePerfMetric(perfMgr, vmMor, null, null, null);
                    if (perfMetrics != null) {
                        for (int index = 0; index < perfMetrics.size(); ++index) {
                            if (((rxPerfCounterInfo != null) && (perfMetrics.get(index).getCounterId() == rxPerfCounterInfo.getKey())) ||
                                    ((txPerfCounterInfo != null) && (perfMetrics.get(index).getCounterId() == txPerfCounterInfo.getKey()))) {
                                vmNetworkMetrics.add(perfMetrics.get(index));
                            }
                        }
                    }

                    double networkReadKBs = 0;
                    double networkWriteKBs = 0;
                    long sampleDuration = 0;

                    if (vmNetworkMetrics.size() != 0) {
                        PerfQuerySpec qSpec = new PerfQuerySpec();
                        qSpec.setEntity(vmMor);
                        PerfMetricId[] availableMetricIds = vmNetworkMetrics.toArray(new PerfMetricId[0]);
                        qSpec.getMetricId().addAll(Arrays.asList(availableMetricIds));
                        List<PerfQuerySpec> qSpecs = new ArrayList<PerfQuerySpec>();
                        qSpecs.add(qSpec);
                        List<PerfEntityMetricBase> values = service.queryPerf(perfMgr, qSpecs);

                        for (int i = 0; i < values.size(); ++i) {
                            List<PerfSampleInfo> infos = ((PerfEntityMetric)values.get(i)).getSampleInfo();
                            if (infos != null && infos.size() > 0) {
                                int endMs = infos.get(infos.size() - 1).getTimestamp().getSecond() * 1000 + infos.get(infos.size() - 1).getTimestamp().getMillisecond();
                                int beginMs = infos.get(0).getTimestamp().getSecond() * 1000 + infos.get(0).getTimestamp().getMillisecond();
                                sampleDuration = (endMs - beginMs) / 1000;
                                List<PerfMetricSeries> vals = ((PerfEntityMetric)values.get(i)).getValue();
                                for (int vi = 0; ((vals != null) && (vi < vals.size())); ++vi) {
                                    if (vals.get(vi) instanceof PerfMetricIntSeries) {
                                        PerfMetricIntSeries val = (PerfMetricIntSeries)vals.get(vi);
                                        List<Long> perfValues = val.getValue();
                                        Long sumRate = 0L;
                                        for (int j = 0; j < infos.size(); j++) { // Size of the array matches the size as the PerfSampleInfo
                                            sumRate += perfValues.get(j);
                                        }
                                        Long averageRate = sumRate / infos.size();
                                        if (vals.get(vi).getId().getCounterId() == rxPerfCounterInfo.getKey()) {
                                            networkReadKBs = sampleDuration * averageRate; //get the average RX rate multiplied by sampled duration
                                        }
                                        if (vals.get(vi).getId().getCounterId() == txPerfCounterInfo.getKey()) {
                                            networkWriteKBs = sampleDuration * averageRate;//get the average TX rate multiplied by sampled duration
                                        }
                                    }
                                }
                            }
                        }
                    }
                    vmResponseMap.put(name, new VmStatsEntry(Integer.parseInt(maxCpuUsage), networkReadKBs, networkWriteKBs, Integer.parseInt(numberCPUs), "vm"));
                }
            }
        }
        return vmResponseMap;
    }


    protected String networkUsage(final String privateIpAddress, final String option, final String ethName) {
        String args = null;
        if (option.equals("get")) {
            args = "-g";
        } else if (option.equals("create")) {
            args = "-c";
        } else if (option.equals("reset")) {
            args = "-r";
        } else if (option.equals("addVif")) {
            args = "-a";
            args += ethName;
        } else if (option.equals("deleteVif")) {
            args = "-d";
            args += ethName;
        }

        ExecutionResult result = executeInVR(privateIpAddress, "netusage.sh", args);

        if (!result.isSuccess()) {
            return null;
        }

        return result.getDetails();
    }

    private long[] getNetworkStats(String privateIP) {
        String result = networkUsage(privateIP, "get", null);
        long[] stats = new long[2];
        if (result != null) {
            try {
                String[] splitResult = result.split(":");
                int i = 0;
                while (i < splitResult.length - 1) {
                    stats[0] += (new Long(splitResult[i++])).longValue();
                    stats[1] += (new Long(splitResult[i++])).longValue();
                }
            } catch (Throwable e) {
                s_logger.warn("Unable to parse return from script return of network usage command: " + e.toString(), e);
            }
        }
        return stats;
    }

    protected String connect(final String vmName, final String ipAddress, final int port) {
        long startTick = System.currentTimeMillis();

        // wait until we have at least been waiting for _ops_timeout time or
        // at least have tried _retry times, this is to coordinate with system
        // VM patching/rebooting time that may need
        int retry = _retry;
        while (System.currentTimeMillis() - startTick <= _opsTimeout || --retry > 0) {
            SocketChannel sch = null;
            try {
                s_logger.info("Trying to connect to " + ipAddress);
                sch = SocketChannel.open();
                sch.configureBlocking(true);
                sch.socket().setSoTimeout(5000);

                InetSocketAddress addr = new InetSocketAddress(ipAddress, port);
                sch.connect(addr);
                return null;
            } catch (IOException e) {
                s_logger.info("Could not connect to " + ipAddress + " due to " + e.toString());
                if (e instanceof ConnectException) {
                    // if connection is refused because of VM is being started,
                    // we give it more sleep time
                    // to avoid running out of retry quota too quickly
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ex) {
                    }
                }
            } finally {
                if (sch != null) {
                    try {
                        sch.close();
                    } catch (IOException e) {
                    }
                }
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
            }
        }

        s_logger.info("Unable to logon to " + ipAddress);

        return "Unable to connect";
    }

    protected String connect(final String vmname, final String ipAddress) {
        return connect(vmname, ipAddress, 3922);
    }

    public static PowerState getVmState(VirtualMachineMO vmMo) throws Exception {
        VirtualMachineRuntimeInfo runtimeInfo = vmMo.getRuntimeInfo();
        return convertPowerState(runtimeInfo.getPowerState());
    }


    private static PowerState convertPowerState(VirtualMachinePowerState powerState) {
        return s_powerStatesTable.get(powerState);
    }

    public static PowerState getVmPowerState(VirtualMachineMO vmMo) throws Exception {
        VirtualMachineRuntimeInfo runtimeInfo = vmMo.getRuntimeInfo();
        return convertPowerState(runtimeInfo.getPowerState());
    }

    private static HostStatsEntry getHyperHostStats(VmwareHypervisorHost hyperHost) throws Exception {
        ComputeResourceSummary hardwareSummary = hyperHost.getHyperHostHardwareSummary();
        if (hardwareSummary == null)
            return null;

        HostStatsEntry entry = new HostStatsEntry();

        entry.setEntityType("host");
        double cpuUtilization = ((double)(hardwareSummary.getTotalCpu() - hardwareSummary.getEffectiveCpu()) / (double)hardwareSummary.getTotalCpu() * 100);
        entry.setCpuUtilization(cpuUtilization);
        entry.setTotalMemoryKBs(hardwareSummary.getTotalMemory() / 1024);
        entry.setFreeMemoryKBs(hardwareSummary.getEffectiveMemory() * 1024);

        return entry;
    }

    private static String getRouterSshControlIp(NetworkElementCommand cmd) {
        String routerIp = cmd.getAccessDetail(NetworkElementCommand.ROUTER_IP);
        String routerGuestIp = cmd.getAccessDetail(NetworkElementCommand.ROUTER_GUEST_IP);
        String zoneNetworkType = cmd.getAccessDetail(NetworkElementCommand.ZONE_NETWORK_TYPE);

        if (routerGuestIp != null && zoneNetworkType != null && NetworkType.valueOf(zoneNetworkType) == NetworkType.Basic) {
            if (s_logger.isDebugEnabled())
                s_logger.debug("In Basic zone mode, use router's guest IP for SSH control. guest IP : " + routerGuestIp);

            return routerGuestIp;
        }

        if (s_logger.isDebugEnabled())
            s_logger.debug("Use router's private IP for SSH control. IP : " + routerIp);
        return routerIp;
    }

    @Override
    public void setAgentControl(IAgentControl agentControl) {
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        try {
            _name = name;

            _url = (String)params.get("url");
            _username = (String)params.get("username");
            _password = (String)params.get("password");
            _dcId = (String)params.get("zone");
            _pod = (String)params.get("pod");
            _cluster = (String)params.get("cluster");

            _guid = (String)params.get("guid");
            String[] tokens = _guid.split("@");
            _vCenterAddress = tokens[1];
            _morHyperHost = new ManagedObjectReference();
            String[] hostTokens = tokens[0].split(":");
            _morHyperHost.setType(hostTokens[0]);
            _morHyperHost.setValue(hostTokens[1]);

            _guestTrafficInfo = (VmwareTrafficLabel)params.get("guestTrafficInfo");
            _publicTrafficInfo = (VmwareTrafficLabel)params.get("publicTrafficInfo");
            VmwareContext context = getServiceContext();
            VmwareManager mgr = context.getStockObject(VmwareManager.CONTEXT_STOCK_NAME);
            if (mgr == null) {
                throw new ConfigurationException("Invalid vmwareContext:  vmwareMgr stock object is not set or cleared.");
            }
            mgr.setupResourceStartupParams(params);

            CustomFieldsManagerMO cfmMo = new CustomFieldsManagerMO(context, context.getServiceContent().getCustomFieldsManager());
            cfmMo.ensureCustomFieldDef("Datastore", CustomFieldConstants.CLOUD_UUID);
            if (_publicTrafficInfo != null && _publicTrafficInfo.getVirtualSwitchType() != VirtualSwitchType.StandardVirtualSwitch || _guestTrafficInfo != null &&
                    _guestTrafficInfo.getVirtualSwitchType() != VirtualSwitchType.StandardVirtualSwitch) {
                cfmMo.ensureCustomFieldDef("DistributedVirtualPortgroup", CustomFieldConstants.CLOUD_GC_DVP);
            }
            cfmMo.ensureCustomFieldDef("Network", CustomFieldConstants.CLOUD_GC);
            cfmMo.ensureCustomFieldDef("VirtualMachine", CustomFieldConstants.CLOUD_UUID);
            cfmMo.ensureCustomFieldDef("VirtualMachine", CustomFieldConstants.CLOUD_NIC_MASK);
            cfmMo.ensureCustomFieldDef("VirtualMachine", CustomFieldConstants.CLOUD_VM_INTERNAL_NAME);
            cfmMo.ensureCustomFieldDef("VirtualMachine", CustomFieldConstants.CLOUD_WORKER);
            cfmMo.ensureCustomFieldDef("VirtualMachine", CustomFieldConstants.CLOUD_WORKER_TAG);

            VmwareHypervisorHost hostMo = this.getHyperHost(context);
            _hostName = hostMo.getHyperHostName();

            if (_guestTrafficInfo.getVirtualSwitchType() == VirtualSwitchType.NexusDistributedVirtualSwitch ||
                    _publicTrafficInfo.getVirtualSwitchType() == VirtualSwitchType.NexusDistributedVirtualSwitch) {
                _privateNetworkVSwitchName = mgr.getPrivateVSwitchName(Long.parseLong(_dcId), HypervisorType.VMware);
                _vsmCredentials = mgr.getNexusVSMCredentialsByClusterId(Long.parseLong(_cluster));
            }

            if (_privateNetworkVSwitchName == null) {
                _privateNetworkVSwitchName = (String)params.get("private.network.vswitch.name");
            }

            String value = (String)params.get("vmware.recycle.hung.wokervm");
            if (value != null && value.equalsIgnoreCase("true"))
                _recycleHungWorker = true;

            value = (String)params.get("vmware.root.disk.controller");
            if (value != null && value.equalsIgnoreCase("scsi"))
                _rootDiskController = DiskControllerType.scsi;
            else
                _rootDiskController = DiskControllerType.ide;

            Integer intObj = (Integer)params.get("ports.per.dvportgroup");
            if (intObj != null)
                _portsPerDvPortGroup = intObj.intValue();

            s_logger.info("VmwareResource network configuration info." + " private traffic over vSwitch: " + _privateNetworkVSwitchName + ", public traffic over " +
                    _publicTrafficInfo.getVirtualSwitchType() + " : " + _publicTrafficInfo.getVirtualSwitchName() + ", guest traffic over " +
                    _guestTrafficInfo.getVirtualSwitchType() + " : " + _guestTrafficInfo.getVirtualSwitchName());

            Boolean boolObj = (Boolean)params.get("vmware.create.full.clone");
            if (boolObj != null && boolObj.booleanValue()) {
                _fullCloneFlag = true;
            } else {
                _fullCloneFlag = false;
            }

            boolObj = (Boolean)params.get("vm.instancename.flag");
            if (boolObj != null && boolObj.booleanValue()) {
                _instanceNameFlag = true;
            } else {
                _instanceNameFlag = false;
            }

            value = (String)params.get("scripts.timeout");
            int timeout = NumbersUtil.parseInt(value, 1440) * 1000;
            _storageProcessor = new VmwareStorageProcessor((VmwareHostService)this, _fullCloneFlag, (VmwareStorageMount)mgr, timeout, this, _shutdownWaitMs, null);
            storageHandler = new VmwareStorageSubsystemCommandHandler(_storageProcessor);

            _vrResource = new VirtualRoutingResource(this);
            if (!_vrResource.configure(name, params)) {
                throw new ConfigurationException("Unable to configure VirtualRoutingResource");
            }

            if (s_logger.isTraceEnabled()) {
                s_logger.trace("Successfully configured VmwareResource.");
            }
            return true;
        } catch (Exception e) {
            s_logger.error("Unexpected Exception ", e);
            throw new ConfigurationException("Failed to configure VmwareResource due to unexpect exception.");
        } finally {
            recycleServiceContext();
        }
    }

    @Override
    public String getName() {
        return _name;
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    public VmwareContext getServiceContext() {
        return getServiceContext(null);
    }

    public void invalidateServiceContext() {
        invalidateServiceContext(null);
    }

    public VmwareHypervisorHost getHyperHost(VmwareContext context) {
        return getHyperHost(context, null);
    }

    @Override
    public VmwareContext getServiceContext(Command cmd) {
        VmwareContext context = null;
        if(s_serviceContext.get() != null) {
            context = s_serviceContext.get();
            String poolKey = VmwareContextPool.composePoolKey(_vCenterAddress, _username);
            // Before re-using the thread local context, ensure it corresponds to the right vCenter API session and that it is valid to make calls.
            if(context.getPoolKey().equals(poolKey)) {
                if (context.validate()) {
                    if (s_logger.isTraceEnabled()) {
                        s_logger.trace("ThreadLocal context is still valid, just reuse");
                    }
                    return context;
                } else {
                    s_logger.info("Validation of the context failed, dispose and use a new one");
                    invalidateServiceContext(context);
                }
            } else {
                // Exisitng ThreadLocal context corresponds to a different vCenter API session. Why has it not been recycled?
                s_logger.warn("ThreadLocal VMware context: " + poolKey + " doesn't correspond to the right vCenter. Expected VMware context: " + context.getPoolKey());
            }
        }
        try {
            context = VmwareContextFactory.getContext(_vCenterAddress, _username, _password);
            s_serviceContext.set(context);
        } catch (Exception e) {
            s_logger.error("Unable to connect to vSphere server: " + _vCenterAddress, e);
            throw new CloudRuntimeException("Unable to connect to vSphere server: " + _vCenterAddress);
        }
        return context;
    }

    @Override
    public void invalidateServiceContext(VmwareContext context) {
        assert (s_serviceContext.get() == context);

        s_serviceContext.set(null);
        if (context != null)
            context.close();
    }

    private static void recycleServiceContext() {
        VmwareContext context = s_serviceContext.get();
        if (s_logger.isTraceEnabled()) {
            s_logger.trace("Reset threadlocal context to null");
        }
        s_serviceContext.set(null);

        if (context != null) {
            assert (context.getPool() != null);
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("Recycling threadlocal context to pool");
            }
            context.getPool().returnContext(context);
        }
    }

    @Override
    public VmwareHypervisorHost getHyperHost(VmwareContext context, Command cmd) {
        if (_morHyperHost.getType().equalsIgnoreCase("HostSystem")) {
            return new HostMO(context, _morHyperHost);
        }
        return new ClusterMO(context, _morHyperHost);
    }

    @Override
    @DB
    public String getWorkerName(VmwareContext context, Command cmd, int workerSequence) {
        VmwareManager mgr = context.getStockObject(VmwareManager.CONTEXT_STOCK_NAME);
        String vmName = mgr.composeWorkerName();

        assert (cmd != null);
        context.getStockObject(VmwareManager.CONTEXT_STOCK_NAME);
        // TODO: Fix this? long checkPointId = vmwareMgr.pushCleanupCheckpoint(this._guid, vmName);
        // TODO: Fix this? cmd.setContextParam("checkpoint", String.valueOf(checkPointId));
        return vmName;
    }

    @Override
    public void setName(String name) {
        // TODO Auto-generated method stub
    }

    @Override
    public void setConfigParams(Map<String, Object> params) {
        // TODO Auto-generated method stub

    }

    @Override
    public Map<String, Object> getConfigParams() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getRunLevel() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void setRunLevel(int level) {
        // TODO Auto-generated method stub
    }

    @Override
    public Answer execute(DestroyCommand cmd) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Executing resource DestroyCommand to evict template from storage pool: " + _gson.toJson(cmd));
        }

        try {
            VmwareContext context = getServiceContext(null);
            VmwareHypervisorHost hyperHost = getHyperHost(context, null);
            VolumeTO vol = cmd.getVolume();

            ManagedObjectReference morDs = HypervisorHostHelper.findDatastoreWithBackwardsCompatibility(hyperHost, vol.getPoolUuid());
            if (morDs == null) {
                String msg = "Unable to find datastore based on volume mount point " + vol.getMountPoint();
                s_logger.error(msg);
                throw new Exception(msg);
            }

            ManagedObjectReference morCluster = hyperHost.getHyperHostCluster();
            ClusterMO clusterMo = new ClusterMO(context, morCluster);

            VirtualMachineMO vmMo = clusterMo.findVmOnHyperHost(vol.getPath());
            if (vmMo != null) {
                if (s_logger.isInfoEnabled()) {
                    s_logger.info("Destroy template volume " + vol.getPath());
                }
                vmMo.destroy();
            } else {
                if (s_logger.isInfoEnabled()) {
                    s_logger.info("Template volume " + vol.getPath() + " is not found, no need to delete.");
                }
            }
            return new Answer(cmd, true, "Success");

        } catch (Throwable e) {
            if (e instanceof RemoteException) {
                s_logger.warn("Encounter remote exception to vCenter, invalidate VMware session context");
                invalidateServiceContext(null);
            }

            String msg = "DestroyCommand failed due to " + VmwareHelper.getExceptionMessage(e);
            s_logger.error(msg, e);
            return new Answer(cmd, false, msg);
        }
    }
}
