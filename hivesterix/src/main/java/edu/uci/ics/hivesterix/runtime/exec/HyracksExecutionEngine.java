package edu.uci.ics.hivesterix.runtime.exec;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.exec.ConditionalTask;
import org.apache.hadoop.hive.ql.exec.FileSinkOperator;
import org.apache.hadoop.hive.ql.exec.MapRedTask;
import org.apache.hadoop.hive.ql.exec.Operator;
import org.apache.hadoop.hive.ql.exec.TableScanOperator;
import org.apache.hadoop.hive.ql.exec.Task;
import org.apache.hadoop.hive.ql.plan.DynamicPartitionCtx;
import org.apache.hadoop.hive.ql.plan.FetchWork;
import org.apache.hadoop.hive.ql.plan.FileSinkDesc;
import org.apache.hadoop.hive.ql.plan.MapredLocalWork;
import org.apache.hadoop.hive.ql.plan.MapredWork;
import org.apache.hadoop.hive.ql.plan.PartitionDesc;
import org.apache.hadoop.hive.ql.plan.TableScanDesc;

import edu.uci.ics.hivesterix.logical.expression.HiveExpressionTypeComputer;
import edu.uci.ics.hivesterix.logical.expression.HiveMergeAggregationExpressionFactory;
import edu.uci.ics.hivesterix.logical.expression.HiveNullableTypeComputer;
import edu.uci.ics.hivesterix.logical.expression.HivePartialAggregationTypeComputer;
import edu.uci.ics.hivesterix.logical.plan.HiveAlgebricksTranslator;
import edu.uci.ics.hivesterix.logical.plan.HiveLogicalPlanAndMetaData;
import edu.uci.ics.hivesterix.optimizer.rulecollections.HiveRuleCollections;
import edu.uci.ics.hivesterix.runtime.config.ConfUtil;
import edu.uci.ics.hivesterix.runtime.factory.evaluator.HiveExpressionRuntimeProvider;
import edu.uci.ics.hivesterix.runtime.factory.nullwriter.HiveNullWriterFactory;
import edu.uci.ics.hivesterix.runtime.inspector.HiveBinaryBooleanInspectorFactory;
import edu.uci.ics.hivesterix.runtime.inspector.HiveBinaryIntegerInspectorFactory;
import edu.uci.ics.hivesterix.runtime.jobgen.HiveConnectorPolicyAssignmentPolicy;
import edu.uci.ics.hivesterix.runtime.jobgen.HiveConnectorPolicyAssignmentPolicy.Policy;
import edu.uci.ics.hivesterix.runtime.provider.HiveBinaryComparatorFactoryProvider;
import edu.uci.ics.hivesterix.runtime.provider.HiveBinaryHashFunctionFactoryProvider;
import edu.uci.ics.hivesterix.runtime.provider.HiveBinaryHashFunctionFamilyProvider;
import edu.uci.ics.hivesterix.runtime.provider.HiveNormalizedKeyComputerFactoryProvider;
import edu.uci.ics.hivesterix.runtime.provider.HivePrinterFactoryProvider;
import edu.uci.ics.hivesterix.runtime.provider.HiveSerializerDeserializerProvider;
import edu.uci.ics.hivesterix.runtime.provider.HiveTypeTraitProvider;
import edu.uci.ics.hyracks.algebricks.common.constraints.AlgebricksAbsolutePartitionConstraint;
import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.common.utils.Pair;
import edu.uci.ics.hyracks.algebricks.compiler.api.HeuristicCompilerFactoryBuilder;
import edu.uci.ics.hyracks.algebricks.compiler.api.HeuristicCompilerFactoryBuilder.DefaultOptimizationContextFactory;
import edu.uci.ics.hyracks.algebricks.compiler.api.ICompiler;
import edu.uci.ics.hyracks.algebricks.compiler.api.ICompilerFactory;
import edu.uci.ics.hyracks.algebricks.compiler.rewriter.rulecontrollers.SequentialFixpointRuleController;
import edu.uci.ics.hyracks.algebricks.compiler.rewriter.rulecontrollers.SequentialOnceRuleController;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalPlan;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalPlanAndMetadata;
import edu.uci.ics.hyracks.algebricks.core.algebra.prettyprint.LogicalOperatorPrettyPrintVisitor;
import edu.uci.ics.hyracks.algebricks.core.algebra.prettyprint.PlanPrettyPrinter;
import edu.uci.ics.hyracks.algebricks.core.rewriter.base.AbstractRuleController;
import edu.uci.ics.hyracks.algebricks.core.rewriter.base.IAlgebraicRewriteRule;
import edu.uci.ics.hyracks.algebricks.core.rewriter.base.PhysicalOptimizationConfig;
import edu.uci.ics.hyracks.api.client.HyracksConnection;
import edu.uci.ics.hyracks.api.client.IHyracksClientConnection;
import edu.uci.ics.hyracks.api.job.JobId;
import edu.uci.ics.hyracks.api.job.JobSpecification;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class HyracksExecutionEngine implements IExecutionEngine {

    private static final Log LOG = LogFactory.getLog(HyracksExecutionEngine.class.getName());

    // private static final String[] locConstraints = {}

    private static List<Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>> DEFAULT_LOGICAL_REWRITES = new ArrayList<Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>>();
    private static List<Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>> DEFAULT_PHYSICAL_REWRITES = new ArrayList<Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>>();
    static {
        SequentialFixpointRuleController seqCtrlNoDfs = new SequentialFixpointRuleController(false);
        
        //This type of rule means that you apply the rule over and over until no difference in the tree is detected.
        SequentialFixpointRuleController seqCtrlFullDfs = new SequentialFixpointRuleController(true);

        //This type of rule means that you apply the rule only once.
        SequentialOnceRuleController seqOnceCtrl = new SequentialOnceRuleController(true);
        DEFAULT_LOGICAL_REWRITES.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqCtrlFullDfs,
                HiveRuleCollections.NORMALIZATION));
        DEFAULT_LOGICAL_REWRITES.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqCtrlNoDfs,
                HiveRuleCollections.COND_PUSHDOWN_AND_JOIN_INFERENCE));
        DEFAULT_LOGICAL_REWRITES.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqCtrlFullDfs,
                HiveRuleCollections.LOAD_FIELDS));
        DEFAULT_LOGICAL_REWRITES.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqCtrlNoDfs,
                HiveRuleCollections.OP_PUSHDOWN));
        DEFAULT_LOGICAL_REWRITES.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqOnceCtrl,
                HiveRuleCollections.DATA_EXCHANGE));
        DEFAULT_LOGICAL_REWRITES.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqCtrlNoDfs,
                HiveRuleCollections.CONSOLIDATION));

        DEFAULT_PHYSICAL_REWRITES.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqOnceCtrl,
                HiveRuleCollections.PHYSICAL_PLAN_REWRITES));
        DEFAULT_PHYSICAL_REWRITES.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqOnceCtrl,
                HiveRuleCollections.prepareJobGenRules));
        DEFAULT_PHYSICAL_REWRITES.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqOnceCtrl,
                HiveRuleCollections.prepareForMapReduceJobGenRUleCollection));

    }

    /**
     * static configurations for compiler
     */
    private HeuristicCompilerFactoryBuilder builder;

    /**
     * compiler
     */
    private ICompiler compiler;

    /**
     * physical optimization config
     */
    private PhysicalOptimizationConfig physicalOptimizationConfig;

    /**
     * final ending operators
     */
    private List<Operator> leaveOps = new ArrayList<Operator>();

    /**
     * tasks that are already visited
     */
    private Map<Task<? extends Serializable>, Boolean> tasksVisited = new HashMap<Task<? extends Serializable>, Boolean>();

    /**
     * hyracks job spec
     */
    private JobSpecification jobSpec;

    /**
     * hive configuration
     */
    private HiveConf conf;

    /**
     * plan printer
     */
    private PrintWriter planPrinter;

    public HyracksExecutionEngine(HiveConf conf) {
        this.conf = conf;
        init(conf);
    }

    public HyracksExecutionEngine(HiveConf conf, PrintWriter planPrinter) {
        this.conf = conf;
        this.planPrinter = planPrinter;
        init(conf);
    }

    private void init(HiveConf conf) {
        builder = new HeuristicCompilerFactoryBuilder(DefaultOptimizationContextFactory.INSTANCE);
        builder.setLogicalRewrites(DEFAULT_LOGICAL_REWRITES);
        builder.setPhysicalRewrites(DEFAULT_PHYSICAL_REWRITES);
        builder.setIMergeAggregationExpressionFactory(HiveMergeAggregationExpressionFactory.INSTANCE);
        builder.setExpressionTypeComputer(HiveExpressionTypeComputer.INSTANCE);
        builder.setNullableTypeComputer(HiveNullableTypeComputer.INSTANCE);

        long memSizeExternalGby = conf.getLong("hive.algebricks.groupby.external.memory", 268435456);
        long memSizeExternalSort = conf.getLong("hive.algebricks.sort.memory", 536870912);
        int frameSize = conf.getInt("hive.algebricks.framesize", 32768);

        physicalOptimizationConfig = new PhysicalOptimizationConfig();
        int frameLimitExtGby = (int) (memSizeExternalGby / frameSize);
        physicalOptimizationConfig.setMaxFramesExternalGroupBy(frameLimitExtGby);
        int frameLimitExtSort = (int) (memSizeExternalSort / frameSize);
        physicalOptimizationConfig.setMaxFramesExternalSort(frameLimitExtSort);
        builder.setPhysicalOptimizationConfig(physicalOptimizationConfig);
    }

    @Override
    public int compileJob(List<Task<? extends Serializable>> rootTasks) {
        // clean up
        leaveOps.clear();
        tasksVisited.clear();
        jobSpec = null;

        HashMap<String, PartitionDesc> aliasToPath = new HashMap<String, PartitionDesc>();
        List<Operator> rootOps = generateRootOperatorDAG(rootTasks, aliasToPath);

        // get all leave Ops
        getLeaves(rootOps, leaveOps);

        HiveAlgebricksTranslator translator = new HiveAlgebricksTranslator();
        try {
            translator.translate(rootOps, null, aliasToPath);

            ILogicalPlan plan = translator.genLogicalPlan();

            if (plan.getRoots() != null && plan.getRoots().size() > 0 && plan.getRoots().get(0).getValue() != null) {
                translator.printOperators();
                ILogicalPlanAndMetadata planAndMetadata = new HiveLogicalPlanAndMetaData(plan,
                        translator.getMetadataProvider());

                ICompilerFactory compilerFactory = builder.create();
                compiler = compilerFactory.createCompiler(planAndMetadata.getPlan(),
                        planAndMetadata.getMetadataProvider(), translator.getVariableCounter());

                // run optimization and re-writing rules for Hive plan
                compiler.optimize();

                // print optimized plan
                LogicalOperatorPrettyPrintVisitor pvisitor = new LogicalOperatorPrettyPrintVisitor();
                StringBuilder buffer = new StringBuilder();
                PlanPrettyPrinter.printPlan(plan, buffer, pvisitor, 0);
                String planStr = buffer.toString();
                System.out.println(planStr);

                if (planPrinter != null)
                    planPrinter.print(planStr);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        }

        return 0;
    }

    private void codeGen() throws AlgebricksException {
        // number of cpu cores in the cluster
        builder.setClusterLocations(new AlgebricksAbsolutePartitionConstraint(ConfUtil.getNCs()));
        // builder.setClusterTopology(ConfUtil.getClusterTopology());
        builder.setBinaryBooleanInspectorFactory(HiveBinaryBooleanInspectorFactory.INSTANCE);
        builder.setBinaryIntegerInspectorFactory(HiveBinaryIntegerInspectorFactory.INSTANCE);
        builder.setComparatorFactoryProvider(HiveBinaryComparatorFactoryProvider.INSTANCE);
        builder.setExpressionRuntimeProvider(HiveExpressionRuntimeProvider.INSTANCE);
        builder.setHashFunctionFactoryProvider(HiveBinaryHashFunctionFactoryProvider.INSTANCE);
        builder.setPrinterProvider(HivePrinterFactoryProvider.INSTANCE);
        builder.setSerializerDeserializerProvider(HiveSerializerDeserializerProvider.INSTANCE);
        builder.setNullWriterFactory(HiveNullWriterFactory.INSTANCE);
        builder.setNormalizedKeyComputerFactoryProvider(HiveNormalizedKeyComputerFactoryProvider.INSTANCE);
        builder.setPartialAggregationTypeComputer(HivePartialAggregationTypeComputer.INSTANCE);
        builder.setTypeTraitProvider(HiveTypeTraitProvider.INSTANCE);
        builder.setHashFunctionFamilyProvider(HiveBinaryHashFunctionFamilyProvider.INSTANCE);

        jobSpec = compiler.createJob(null);

        // set the policy
        String policyStr = conf.get("hive.hyracks.connectorpolicy");
        if (policyStr == null)
            policyStr = "PIPELINING";
        Policy policyValue = Policy.valueOf(policyStr);
        jobSpec.setConnectorPolicyAssignmentPolicy(new HiveConnectorPolicyAssignmentPolicy(policyValue));
        jobSpec.setUseConnectorPolicyForScheduling(false);
    }

    @Override
    public int executeJob() {
        try {
            codeGen();
            executeHyraxJob(jobSpec);
        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        }
        return 0;
    }

    private List<Operator> generateRootOperatorDAG(List<Task<? extends Serializable>> rootTasks,
            HashMap<String, PartitionDesc> aliasToPath) {

        List<Operator> rootOps = new ArrayList<Operator>();
        List<Task<? extends Serializable>> toDelete = new ArrayList<Task<? extends Serializable>>();
        tasksVisited.clear();

        for (int i = rootTasks.size() - 1; i >= 0; i--) {
            /**
             * list of map-reduce tasks
             */
            Task<? extends Serializable> task = rootTasks.get(i);
            // System.out.println("!" + task.getName());

            if (task instanceof MapRedTask) {
                List<Operator> mapRootOps = articulateMapReduceOperators(task, rootOps, aliasToPath, rootTasks);
                if (i == 0)
                    rootOps.addAll(mapRootOps);
                else {
                    List<Operator> leaves = new ArrayList<Operator>();
                    getLeaves(rootOps, leaves);

                    List<Operator> mapChildren = new ArrayList<Operator>();
                    for (Operator childMap : mapRootOps) {
                        if (childMap instanceof TableScanOperator) {
                            TableScanDesc topDesc = (TableScanDesc) childMap.getConf();
                            if (topDesc == null)
                                mapChildren.add(childMap);
                            else {
                                rootOps.add(childMap);
                            }
                        } else
                            mapChildren.add(childMap);
                    }

                    if (mapChildren.size() > 0) {
                        for (Operator leaf : leaves)
                            leaf.setChildOperators(mapChildren);
                        for (Operator child : mapChildren)
                            child.setParentOperators(leaves);
                    }
                }

                MapredWork mr = (MapredWork) task.getWork();
                HashMap<String, PartitionDesc> map = mr.getAliasToPartnInfo();

                addAliasToPartition(aliasToPath, map);
                toDelete.add(task);
            }
        }

        for (Task<? extends Serializable> task : toDelete)
            rootTasks.remove(task);

        return rootOps;
    }

    private void addAliasToPartition(HashMap<String, PartitionDesc> aliasToPath, HashMap<String, PartitionDesc> map) {
        Iterator<String> keys = map.keySet().iterator();
        while (keys.hasNext()) {
            String key = keys.next();
            PartitionDesc part = map.get(key);
            String[] names = key.split(":");
            for (String name : names) {
                aliasToPath.put(name, part);
            }
        }
    }

    private List<Operator> articulateMapReduceOperators(Task task, List<Operator> rootOps,
            HashMap<String, PartitionDesc> aliasToPath, List<Task<? extends Serializable>> rootTasks) {
        // System.out.println("!"+task.getName());
        if (!(task instanceof MapRedTask)) {
            if (!(task instanceof ConditionalTask)) {
                rootTasks.add(task);
                return null;
            } else {
                // remove map-reduce branches in condition task
                ConditionalTask condition = (ConditionalTask) task;
                List<Task<? extends Serializable>> branches = condition.getListTasks();
                for (int i = branches.size() - 1; i >= 0; i--) {
                    Task branch = branches.get(i);
                    if (branch instanceof MapRedTask) {
                        return articulateMapReduceOperators(branch, rootOps, aliasToPath, rootTasks);
                    }
                }
                rootTasks.add(task);
                return null;
            }
        }

        MapredWork mr = (MapredWork) task.getWork();
        HashMap<String, PartitionDesc> map = mr.getAliasToPartnInfo();

        // put all aliasToParitionDesc mapping into the map
        addAliasToPartition(aliasToPath, map);

        MapRedTask mrtask = (MapRedTask) task;
        MapredWork work = (MapredWork) mrtask.getWork();
        HashMap<String, Operator<? extends Serializable>> operators = work.getAliasToWork();

        Set entries = operators.entrySet();
        Iterator<Entry<String, Operator>> iterator = entries.iterator();
        List<Operator> mapRootOps = new ArrayList<Operator>();

        // get map root operators
        while (iterator.hasNext()) {
            Operator next = iterator.next().getValue();
            if (!mapRootOps.contains(next)) {
                // clear that only for the case of union
                mapRootOps.add(next);
            }
        }

        // get map local work
        MapredLocalWork localWork = work.getMapLocalWork();
        if (localWork != null) {
            HashMap<String, Operator<? extends Serializable>> localOperators = localWork.getAliasToWork();

            Set localEntries = localOperators.entrySet();
            Iterator<Entry<String, Operator>> localIterator = localEntries.iterator();
            while (localIterator.hasNext()) {
                mapRootOps.add(localIterator.next().getValue());
            }

            HashMap<String, FetchWork> localFetch = localWork.getAliasToFetchWork();
            Set localFetchEntries = localFetch.entrySet();
            Iterator<Entry<String, FetchWork>> localFetchIterator = localFetchEntries.iterator();
            while (localFetchIterator.hasNext()) {
                Entry<String, FetchWork> fetchMap = localFetchIterator.next();
                FetchWork fetch = fetchMap.getValue();
                String alias = fetchMap.getKey();
                List<PartitionDesc> dirPart = fetch.getPartDesc();

                // temporary hack: put the first partitionDesc into the map
                aliasToPath.put(alias, dirPart.get(0));
            }
        }

        Boolean visited = tasksVisited.get(task);
        if (visited != null && visited.booleanValue() == true) {
            return mapRootOps;
        }

        // do that only for union operator
        for (Operator op : mapRootOps)
            if (op.getParentOperators() != null)
                op.getParentOperators().clear();

        List<Operator> mapLeaves = new ArrayList<Operator>();
        downToLeaves(mapRootOps, mapLeaves);
        List<Operator> reduceOps = new ArrayList<Operator>();

        if (work.getReducer() != null)
            reduceOps.add(work.getReducer());

        for (Operator mapLeaf : mapLeaves) {
            mapLeaf.setChildOperators(reduceOps);
        }

        for (Operator reduceOp : reduceOps) {
            if (reduceOp != null)
                reduceOp.setParentOperators(mapLeaves);
        }

        List<Operator> leafs = new ArrayList<Operator>();
        if (reduceOps.size() > 0) {
            downToLeaves(reduceOps, leafs);
        } else {
            leafs = mapLeaves;
        }

        List<Operator> mapChildren = new ArrayList<Operator>();
        if (task.getChildTasks() != null && task.getChildTasks().size() > 0) {
            for (Object child : task.getChildTasks()) {
                List<Operator> childMapOps = articulateMapReduceOperators((Task) child, rootOps, aliasToPath, rootTasks);
                if (childMapOps == null)
                    continue;

                for (Operator childMap : childMapOps) {
                    if (childMap instanceof TableScanOperator) {
                        TableScanDesc topDesc = (TableScanDesc) childMap.getConf();
                        if (topDesc == null)
                            mapChildren.add(childMap);
                        else {
                            rootOps.add(childMap);
                        }
                    } else {
                        // if not table scan, add the child
                        mapChildren.add(childMap);
                    }
                }
            }

            if (mapChildren.size() > 0) {
                int i = 0;
                for (Operator leaf : leafs) {
                    if (leaf.getChildOperators() == null || leaf.getChildOperators().size() == 0)
                        leaf.setChildOperators(new ArrayList<Operator>());
                    leaf.getChildOperators().add(mapChildren.get(i));
                    i++;
                }
                i = 0;
                for (Operator child : mapChildren) {
                    if (child.getParentOperators() == null || child.getParentOperators().size() == 0)
                        child.setParentOperators(new ArrayList<Operator>());
                    child.getParentOperators().add(leafs.get(i));
                    i++;
                }
            }
        }

        // mark this task as visited
        this.tasksVisited.put(task, true);
        return mapRootOps;
    }

    /**
     * down to leaf nodes
     * 
     * @param ops
     * @param leaves
     */
    private void downToLeaves(List<Operator> ops, List<Operator> leaves) {

        // Operator currentOp;
        for (Operator op : ops) {
            if (op != null && op.getChildOperators() != null && op.getChildOperators().size() > 0) {
                downToLeaves(op.getChildOperators(), leaves);
            } else {
                if (op != null && leaves.indexOf(op) < 0)
                    leaves.add(op);
            }
        }
    }

    private void getLeaves(List<Operator> roots, List<Operator> currentLeaves) {
        for (Operator op : roots) {
            List<Operator> children = op.getChildOperators();
            if (children == null || children.size() <= 0) {
                currentLeaves.add(op);
            } else {
                getLeaves(children, currentLeaves);
            }
        }
    }

    private void executeHyraxJob(JobSpecification job) throws Exception {
        String ipAddress = conf.get("hive.hyracks.host");
        int port = Integer.parseInt(conf.get("hive.hyracks.port"));
        String applicationName = conf.get("hive.hyracks.app");
        //System.out.println("connect to " + ipAddress + " " + port);

        IHyracksClientConnection hcc = new HyracksConnection(ipAddress, port);

        //System.out.println("get connected");
        long start = System.currentTimeMillis();
        JobId jobId = hcc.startJob(applicationName, job);
        hcc.waitForCompletion(jobId);

        //System.out.println("job finished: " + jobId.toString());
        // call all leave nodes to end
        for (Operator leaf : leaveOps) {
            jobClose(leaf);
        }

        long end = System.currentTimeMillis();
        System.err.println(start + " " + end + " " + (end - start));
    }

    /**
     * mv to final directory on hdfs (not real final)
     * 
     * @param leaf
     * @throws Exception
     */
    private void jobClose(Operator leaf) throws Exception {
        FileSinkOperator fsOp = (FileSinkOperator) leaf;
        FileSinkDesc desc = fsOp.getConf();
        boolean isNativeTable = !desc.getTableInfo().isNonNative();
        if ((conf != null) && isNativeTable) {
            String specPath = desc.getDirName();
            DynamicPartitionCtx dpCtx = desc.getDynPartCtx();
            // for 0.7.0
            fsOp.mvFileToFinalPath(specPath, conf, true, LOG, dpCtx);
            // for 0.8.0
            // Utilities.mvFileToFinalPath(specPath, conf, true, LOG, dpCtx,
            // desc);
        }
    }
}
