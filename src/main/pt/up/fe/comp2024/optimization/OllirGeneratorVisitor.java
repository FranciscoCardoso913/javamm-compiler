package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.comp2024.optimization.OptUtils;

import java.util.List;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";
    private final SymbolTable table;

    private final OllirExprGeneratorVisitor exprVisitor;
    // TODO: Add tabs to ollir code
    private int tabs = 0;
    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }


    @Override
    protected void buildVisitor() {
        addVisit(PROGRAM, this::visitProgram);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(VAR_DECL, this::visitVarDecl);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(EXPR_STMT, this::visitExprStmt);

        setDefaultVisit(this::defaultVisit);
    }


    private String visitAssignStmt(JmmNode node, Void unused) {
        // TODO: assignments with lhs equal to a class field need to use putfield(), implement here
        System.out.println("Entered assign");
        var rhs = exprVisitor.visit(node.getJmmChild(0));

        StringBuilder code = new StringBuilder();

        // code to compute the children
        code.append(rhs.getComputation());

        // code to compute self
        // statement has type of lhs
        String typeString = OptUtils.toOllirType(node.getChild(0));

        // TODO: Refactor into two different functions
        if (NodeUtils.isFieldRef(node.get("name"), table, node.getAncestor(METHOD_DECL).get().get("name"))) {
            code.append("putfield(this, ").append(node.get("name")).append(typeString).append(", ")
                    .append(rhs.getCode()).append(").V").append(END_STMT);
        } else {
            code.append(node.get("name"));
            code.append(typeString);
            code.append(SPACE);

            code.append(ASSIGN);
            code.append(typeString);
            code.append(SPACE);

            code.append(rhs.getCode());

            code.append(END_STMT);
        }

        System.out.println("Exited assign");
        return code.toString();
    }


    private String visitReturn(JmmNode node, Void unused) {
        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder();

        var expr = OllirExprResult.EMPTY;

        if (node.getNumChildren() > 0) {
            expr = exprVisitor.visit(node.getJmmChild(0));
        }

        code.append(expr.getComputation());
        code.append("ret");
        code.append(OptUtils.toOllirType(retType));
        code.append(SPACE);

        code.append(expr.getCode());

        code.append(END_STMT);

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {

        var typeCode = OptUtils.toOllirType(node.getJmmChild(0));
        var id = node.get("name");

        String code = id + typeCode;

        return code;
    }


    private String visitMethodDecl(JmmNode node, Void unused) {
        // TODO: Need to change code to support constructors (add invokespecial at the end of the method)

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");
        boolean isStatic = NodeUtils.getBooleanAttribute(node, "isStatic", "false");

        if (isPublic) {
            code.append("public ");
        }

        if (isStatic) {
            code.append("static ");
        }

        // name
        var name = node.get("name");
        code.append(name);

        // params
        code.append("(").append(getParamsCode(name)).append(")");

        // type
        code.append(OptUtils.toOllirType(table.getReturnType(name)));
        code.append(L_BRACKET);


        // rest of its children stmts
        var afterParam = 1 + table.getParameters(name).size();
        for (int i = afterParam; i < node.getNumChildren(); i++) {
            var child = node.getJmmChild(i);
            if (!child.isInstance(VAR_DECL)) {
                var childCode = visit(child);
                code.append(childCode);
            }
        }

        if (node.getChildren(RETURN_STMT).isEmpty())
            code.append("ret.V").append(END_STMT);

        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }

    private String getParamsCode(String methodName) {
        StringBuilder code = new StringBuilder();

        List<Symbol> params = table.getParameters(methodName);
        int listSize = params.size();

        for (int i = 0 ; i < listSize; i++) {
            Symbol param = params.get(i);
            code.append(param.getName());
            code.append(OptUtils.toOllirType(param.getType()));

            if (i != listSize - 1) {
                code.append(", ");
            }
        }

        return code.toString();
    }

    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        boolean hasExtend = table.getSuper() != null;

        code.append(table.getClassName());
        if (hasExtend) {
            code.append(" extends ").append(table.getSuper());
        }
        code.append(L_BRACKET);

        code.append(NL);

        var needNl = true;

        for (var child : node.getChildren()) {
            var result = visit(child);

            if (METHOD_DECL.check(child) && needNl) {
                code.append(NL);
                needNl = false;
            }

            code.append(result);
        }

        code.append(buildConstructor());

        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {

        return ".construct " + table.getClassName() + "().V {\n" +
                "invokespecial(this, \"<init>\").V;\n" +
                "}\n";
    }

    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(buildImports());

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);


        return code.toString();
    }

    private String buildImports () {
        StringBuilder code = new StringBuilder();

        for (String importStmt : table.getImports()) {
            code.append("import ").append(importStmt).append(END_STMT);
        }

        if (!table.getImports().isEmpty()) code.append(NL);

        return code.toString();
    }

    private String visitVarDecl(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // TODO: Change when tree is fully annotated

        JmmNode typeNode = node.getChild(0);
        Type type = new Type(typeNode.get("name"), NodeUtils.getBooleanAttribute(typeNode, "isArray", "false"));
        String varField = OptUtils.toOllirType(type);
        code.append(".field public ").append(node.get("name")).append(varField).append(END_STMT);

        return code.toString();
    }

    private String visitExprStmt(JmmNode node, Void unused) {
        var res = exprVisitor.visit(node.getChild(0));
        return res.getComputation();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        System.out.println(node);

        for (var child : node.getChildren()) {
            code.append(visit(child));
        }

        return code.toString();
    }
}
