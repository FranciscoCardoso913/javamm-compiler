package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.regex.Matcher;

import static pt.up.fe.comp2024.ast.TypeUtils.areTypesAssignable;
import static pt.up.fe.comp2024.ast.TypeUtils.isArray;

public class Statements extends AnalysisVisitor {
    @Override
    protected void buildVisitor() {
        addVisit(Kind.WHILE_STMT, this::visitWhileStatement);
        addVisit(Kind.IF_STMT, this::visitIfStatement);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStatement);
        addVisit(Kind.EXPR_STMT, this::visitExpressionStatement);
        addVisit(Kind.METHOD, this::visitMethod);
        addVisit(Kind.LIST_ASSIGN_STMT,this::visitListAssignStatement);
    }

    private Void visitWhileStatement(JmmNode node, SymbolTable table) {
        if(!node.getChild(0).get("node_type").equals("boolean")){
           addSemanticReport(node, String.format(
                   "While statement should receive type boolean, got %s instead",
                   node.getChild(0).get("node_type")
           ));
        }
        return null;
    }

    private Void visitAssignStatement(JmmNode node, SymbolTable table) {
        String variable_type = node.get("node_type");
        var expr = node.getChild(0);
        if(! areTypesAssignable(expr.get("node_type"), variable_type, table)) {
            addSemanticReport(node, String.format(
                    "Variable of type %s cannot be assign a value of type %s.",
                    variable_type,
                    expr.get("node_type")
            ));
        }
        if(expr.get("node_type").equals("unknown")) expr.put("node_type", variable_type);
        return null;
    }

    private Void visitIfStatement(JmmNode node, SymbolTable table) {
        if(!node.getChild(0).get("node_type").equals("boolean")){
           addSemanticReport(node, String.format(
                   "If statement should receive type boolean, got %s instead",
                   node.getChild(0).get("node_type")
           ));
        }
        return null;
    }
    private Void visitMethod(JmmNode node, SymbolTable table) {
        currentMethod = node.get("name");
        var return_statements = node.getChildren(Kind.RETURN_STMT);
        String returnValueType;
        if(return_statements.isEmpty())
            returnValueType = "void";
        else
            returnValueType = return_statements.get(0).getChild(0).get("node_type");

        if(!node.get("node_type").equals(returnValueType) && !returnValueType.equals("unknown")){
            addSemanticReport(node, String.format(
                    "Method of type %s should return type %s, got %s instead.",
                    node.get("node_type"),
                    node.get("node_type"),
                    returnValueType
            ));
        }

        return null;
    }

    private Void visitExpressionStatement(JmmNode node, SymbolTable table) {
        if(!node.getChildren(Kind.METHOD_EXPR).isEmpty()) node.getChild(0).put("node_type", "void");
        return null;
    }
    private Void visitListAssignStatement(JmmNode node, SymbolTable table) {
        var index_type = node.getChild(0).get("node_type");
        var expr_type = node.getChild(1).get("node_type");
        if(!index_type.equals("int")) addSemanticReport(node, String.format(
                "Expected array index of type int, got %s instead",
                index_type
        ));
        if( !isArray(node.get("node_type"))) addSemanticReport(node, String.format(
                "Expected %s to be array, got %s instead",
                node.get("name"),
                node.get("node_type")
        ));
        Matcher matcher = array_pattern.matcher(node.get("node_type"));
        matcher.find();
        var var_type =matcher.group(1);
        if( ! var_type.equals(expr_type)) addSemanticReport(node, String.format(
                "Variable of type %s cannot be assign a value of type %s.",
                var_type,
                expr_type
        ));
        return null;
    }

}
