package edu.uci.ics.hyracks.algebricks.rewriter.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;

import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.common.utils.Pair;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.IOptimizationContext;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalVariable;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.AbstractFunctionCallExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.AggregateFunctionCallExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.ConstantExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.VariableReferenceExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.functions.IFunctionInfo;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AbstractLogicalOperator.ExecutionMode;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AggregateOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AssignOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.GroupByOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.NestedTupleSourceOperator;
import edu.uci.ics.hyracks.algebricks.core.rewriter.base.IAlgebraicRewriteRule;

public abstract class AbstractIntroduceCombinerRule implements IAlgebraicRewriteRule {
	
	@Override
    public boolean rewritePre(Mutable<ILogicalOperator> opRef, IOptimizationContext context) {
        return false;
    }
	
	/**
	 * Replace the original aggregate functions with their corresponding global aggregate function. 
	 */
	public void replaceOriginalAggFuncs(Map<AggregateFunctionCallExpression, SimilarAggregatesInfo> toReplaceMap) {
		for (Map.Entry<AggregateFunctionCallExpression, SimilarAggregatesInfo> entry : toReplaceMap.entrySet()) {
            SimilarAggregatesInfo sai = entry.getValue();
            for (AggregateExprInfo aei : sai.simAggs) {
                AbstractFunctionCallExpression afce = (AbstractFunctionCallExpression) aei.aggExprRef.getValue();
                afce.setFunctionInfo(aei.newFunInfo);
                afce.getArguments().clear();
                afce.getArguments().add(new MutableObject<ILogicalExpression>(sai.stepOneResult));
            }
        }
	}
	
	protected Pair<Boolean, Mutable<ILogicalOperator>> tryToPushAgg(AggregateOperator initAgg, GroupByOperator newGbyOp,
            Map<AggregateFunctionCallExpression, SimilarAggregatesInfo> toReplaceMap, IOptimizationContext context) throws AlgebricksException {

        ArrayList<LogicalVariable> pushedVars = new ArrayList<LogicalVariable>();
        ArrayList<Mutable<ILogicalExpression>> pushedExprs = new ArrayList<Mutable<ILogicalExpression>>();

        List<LogicalVariable> initVars = initAgg.getVariables();
        List<Mutable<ILogicalExpression>> initExprs = initAgg.getExpressions();
        int numExprs = initVars.size();
        
        // First make sure that all agg funcs are two step, otherwise we cannot use local aggs.
        for (int i = 0; i < numExprs; i++) {
            AggregateFunctionCallExpression aggFun = (AggregateFunctionCallExpression) initExprs.get(i).getValue();
            if (!aggFun.isTwoStep()) {
                return new Pair<Boolean, Mutable<ILogicalOperator>>(false, null);
            }
        }

        boolean haveAggToReplace = false;
        for (int i = 0; i < numExprs; i++) {
            Mutable<ILogicalExpression> expRef = initExprs.get(i);
            AggregateFunctionCallExpression aggFun = (AggregateFunctionCallExpression) expRef.getValue();
            IFunctionInfo fi1 = aggFun.getStepOneAggregate();
            // Clone the aggregate's args.
            List<Mutable<ILogicalExpression>> newArgs = new ArrayList<Mutable<ILogicalExpression>>(aggFun
                    .getArguments().size());
            for (Mutable<ILogicalExpression> er : aggFun.getArguments()) {
                newArgs.add(new MutableObject<ILogicalExpression>(er.getValue().cloneExpression()));
            }
            IFunctionInfo fi2 = aggFun.getStepTwoAggregate();
            SimilarAggregatesInfo inf = toReplaceMap.get(aggFun);
            if (inf == null) {
                inf = new SimilarAggregatesInfo();
                LogicalVariable newAggVar = context.newVar();
                pushedVars.add(newAggVar);
                inf.stepOneResult = new VariableReferenceExpression(newAggVar);
                inf.simAggs = new ArrayList<AggregateExprInfo>();
                toReplaceMap.put(aggFun, inf);
                AggregateFunctionCallExpression aggLocal = new AggregateFunctionCallExpression(fi1, false, newArgs);
                pushedExprs.add(new MutableObject<ILogicalExpression>(aggLocal));
            }
            AggregateExprInfo aei = new AggregateExprInfo();
            aei.aggExprRef = expRef;
            aei.newFunInfo = fi2;
            inf.simAggs.add(aei);
            haveAggToReplace = true;
        }

        if (!pushedVars.isEmpty()) {
            AggregateOperator pushedAgg = new AggregateOperator(pushedVars, pushedExprs);
            pushedAgg.setExecutionMode(ExecutionMode.LOCAL);
            // If newGbyOp is null, then we optimizing an aggregate without group by.
            if (newGbyOp != null) {
                // Hook up the nested aggregate op with the outer group by.
                NestedTupleSourceOperator nts = new NestedTupleSourceOperator(new MutableObject<ILogicalOperator>(newGbyOp));
                nts.setExecutionMode(ExecutionMode.LOCAL);
                pushedAgg.getInputs().add(new MutableObject<ILogicalOperator>(nts));
            } else {
                // The local aggregate operator is fed by the input of the original aggregate operator.
                pushedAgg.getInputs().add(new MutableObject<ILogicalOperator>(initAgg.getInputs().get(0).getValue()));
                // Set the partitioning variable in the local agg to ensure it is not projected away.
                context.computeAndSetTypeEnvironmentForOperator(pushedAgg);
                LogicalVariable trueVar = context.newVar();
                // Reintroduce assign op for the global agg partitioning var.
                AssignOperator trueAssignOp = new AssignOperator(trueVar, new MutableObject<ILogicalExpression>(ConstantExpression.TRUE));
                trueAssignOp.getInputs().add(new MutableObject<ILogicalOperator>(pushedAgg));
                context.computeAndSetTypeEnvironmentForOperator(trueAssignOp);
                initAgg.setPartitioningVariable(trueVar);
                initAgg.getInputs().get(0).setValue(trueAssignOp);
            }
            return new Pair<Boolean, Mutable<ILogicalOperator>>(true, new MutableObject<ILogicalOperator>(pushedAgg));
        } else {
            return new Pair<Boolean, Mutable<ILogicalOperator>>(haveAggToReplace, null);
        }
    }
	
	protected class SimilarAggregatesInfo {
        ILogicalExpression stepOneResult;
        List<AggregateExprInfo> simAggs;
    }

	protected class AggregateExprInfo {
        Mutable<ILogicalExpression> aggExprRef;
        IFunctionInfo newFunInfo;
    }

	protected class BookkeepingInfo {
        Map<AggregateFunctionCallExpression, SimilarAggregatesInfo> toReplaceMap = new HashMap<AggregateFunctionCallExpression, SimilarAggregatesInfo>();
        Map<GroupByOperator, List<LogicalVariable>> modifGbyMap = new HashMap<GroupByOperator, List<LogicalVariable>>();
    }
}
