package com.shapesecurity.shift.parser.statements;

import com.shapesecurity.shift.ast.BinaryExpression;
import com.shapesecurity.shift.ast.ExpressionStatement;
import com.shapesecurity.shift.ast.IdentifierExpression;
import com.shapesecurity.shift.ast.operators.BinaryOperator;
import com.shapesecurity.shift.parser.Assertions;
import com.shapesecurity.shift.parser.JsError;
import org.junit.Test;

/**
 * Created by u478 on 6/10/15.
 */
public class ExpressionStatementTest extends Assertions {
  @Test
  public void testExpressionStatement() throws JsError {
    testScript("x", new ExpressionStatement(new IdentifierExpression("x")));

    testScript("x, y", new ExpressionStatement(new BinaryExpression(BinaryOperator.Sequence,
        new IdentifierExpression("x"),new IdentifierExpression("y"))));

    testScript("\\u0061", new ExpressionStatement(new IdentifierExpression("a")));

    testScript("a\\u0061", new ExpressionStatement(new IdentifierExpression("aa")));

    testScript("\\u0061a", new ExpressionStatement(new IdentifierExpression("aa")));

    testScript("\\u0061a ", new ExpressionStatement(new IdentifierExpression("aa")));
  }
}