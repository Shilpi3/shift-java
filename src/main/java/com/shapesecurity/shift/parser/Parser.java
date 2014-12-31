/*
 * Copyright 2014 Shape Security, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.shapesecurity.shift.parser;

import static com.shapesecurity.shift.parser.ErrorMessages.ACCESSOR_DATA_PROPERTY;
import static com.shapesecurity.shift.parser.ErrorMessages.ACCESSOR_GET_SET;
import static com.shapesecurity.shift.parser.ErrorMessages.ILLEGAL_BREAK;
import static com.shapesecurity.shift.parser.ErrorMessages.ILLEGAL_CONTINUE;
import static com.shapesecurity.shift.parser.ErrorMessages.ILLEGAL_RETURN;
import static com.shapesecurity.shift.parser.ErrorMessages.INVALID_LHS_IN_ASSIGNMENT;
import static com.shapesecurity.shift.parser.ErrorMessages.INVALID_LHS_IN_FOR_IN;
import static com.shapesecurity.shift.parser.ErrorMessages.INVALID_PROPERTY_NAME;
import static com.shapesecurity.shift.parser.ErrorMessages.LABEL_REDECLARATION;
import static com.shapesecurity.shift.parser.ErrorMessages.MULTIPLE_DEFAULTS_IN_SWITCH;
import static com.shapesecurity.shift.parser.ErrorMessages.NEWLINE_AFTER_THROW;
import static com.shapesecurity.shift.parser.ErrorMessages.NO_CATCH_OR_FINALLY;
import static com.shapesecurity.shift.parser.ErrorMessages.STRICT_CATCH_VARIABLE;
import static com.shapesecurity.shift.parser.ErrorMessages.STRICT_DELETE;
import static com.shapesecurity.shift.parser.ErrorMessages.STRICT_DUPLICATE_PROPERTY;
import static com.shapesecurity.shift.parser.ErrorMessages.STRICT_FUNCTION_NAME;
import static com.shapesecurity.shift.parser.ErrorMessages.STRICT_LHS_ASSIGNMENT;
import static com.shapesecurity.shift.parser.ErrorMessages.STRICT_LHS_POSTFIX;
import static com.shapesecurity.shift.parser.ErrorMessages.STRICT_LHS_PREFIX;
import static com.shapesecurity.shift.parser.ErrorMessages.STRICT_MODE_WITH;
import static com.shapesecurity.shift.parser.ErrorMessages.STRICT_OCTAL_LITERAL;
import static com.shapesecurity.shift.parser.ErrorMessages.STRICT_PARAM_DUPE;
import static com.shapesecurity.shift.parser.ErrorMessages.STRICT_PARAM_NAME;
import static com.shapesecurity.shift.parser.ErrorMessages.STRICT_RESERVED_WORD;
import static com.shapesecurity.shift.parser.ErrorMessages.STRICT_VAR_NAME;
import static com.shapesecurity.shift.parser.ErrorMessages.UNEXPECTED_TOKEN;
import static com.shapesecurity.shift.parser.ErrorMessages.UNKNOWN_LABEL;

import com.shapesecurity.functional.data.Either;
import com.shapesecurity.functional.data.List;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.functional.data.NonEmptyList;
import com.shapesecurity.shift.ast.Block;
import com.shapesecurity.shift.ast.CatchClause;
import com.shapesecurity.shift.ast.Directive;
import com.shapesecurity.shift.ast.Expression;
import com.shapesecurity.shift.ast.FunctionBody;
import com.shapesecurity.shift.ast.Identifier;
import com.shapesecurity.shift.ast.Node;
import com.shapesecurity.shift.ast.Script;
import com.shapesecurity.shift.ast.SourceLocation;
import com.shapesecurity.shift.ast.Statement;
import com.shapesecurity.shift.ast.SwitchCase;
import com.shapesecurity.shift.ast.SwitchDefault;
import com.shapesecurity.shift.ast.VariableDeclaration;
import com.shapesecurity.shift.ast.VariableDeclaration.VariableDeclarationKind;
import com.shapesecurity.shift.ast.VariableDeclarator;
import com.shapesecurity.shift.ast.directive.UnknownDirective;
import com.shapesecurity.shift.ast.directive.UseStrictDirective;
import com.shapesecurity.shift.ast.expression.ArrayExpression;
import com.shapesecurity.shift.ast.expression.AssignmentExpression;
import com.shapesecurity.shift.ast.expression.BinaryExpression;
import com.shapesecurity.shift.ast.expression.CallExpression;
import com.shapesecurity.shift.ast.expression.ComputedMemberExpression;
import com.shapesecurity.shift.ast.expression.ConditionalExpression;
import com.shapesecurity.shift.ast.expression.FunctionExpression;
import com.shapesecurity.shift.ast.expression.IdentifierExpression;
import com.shapesecurity.shift.ast.expression.LeftHandSideExpression;
import com.shapesecurity.shift.ast.expression.LiteralBooleanExpression;
import com.shapesecurity.shift.ast.expression.LiteralNullExpression;
import com.shapesecurity.shift.ast.expression.LiteralNumericExpression;
import com.shapesecurity.shift.ast.expression.LiteralRegExpExpression;
import com.shapesecurity.shift.ast.expression.LiteralStringExpression;
import com.shapesecurity.shift.ast.expression.MemberExpression;
import com.shapesecurity.shift.ast.expression.NewExpression;
import com.shapesecurity.shift.ast.expression.ObjectExpression;
import com.shapesecurity.shift.ast.expression.PostfixExpression;
import com.shapesecurity.shift.ast.expression.PrefixExpression;
import com.shapesecurity.shift.ast.expression.StaticMemberExpression;
import com.shapesecurity.shift.ast.expression.ThisExpression;
import com.shapesecurity.shift.ast.operators.Assignment;
import com.shapesecurity.shift.ast.operators.BinaryOperator;
import com.shapesecurity.shift.ast.operators.PostfixOperator;
import com.shapesecurity.shift.ast.operators.Precedence;
import com.shapesecurity.shift.ast.operators.PrefixOperator;
import com.shapesecurity.shift.ast.property.DataProperty;
import com.shapesecurity.shift.ast.property.Getter;
import com.shapesecurity.shift.ast.property.ObjectProperty;
import com.shapesecurity.shift.ast.property.ObjectProperty.ObjectPropertyKind;
import com.shapesecurity.shift.ast.property.PropertyName;
import com.shapesecurity.shift.ast.property.Setter;
import com.shapesecurity.shift.ast.statement.BlockStatement;
import com.shapesecurity.shift.ast.statement.BreakStatement;
import com.shapesecurity.shift.ast.statement.ContinueStatement;
import com.shapesecurity.shift.ast.statement.DebuggerStatement;
import com.shapesecurity.shift.ast.statement.DoWhileStatement;
import com.shapesecurity.shift.ast.statement.EmptyStatement;
import com.shapesecurity.shift.ast.statement.ExpressionStatement;
import com.shapesecurity.shift.ast.statement.ForInStatement;
import com.shapesecurity.shift.ast.statement.ForStatement;
import com.shapesecurity.shift.ast.statement.FunctionDeclaration;
import com.shapesecurity.shift.ast.statement.IfStatement;
import com.shapesecurity.shift.ast.statement.LabeledStatement;
import com.shapesecurity.shift.ast.statement.ReturnStatement;
import com.shapesecurity.shift.ast.statement.SwitchStatement;
import com.shapesecurity.shift.ast.statement.SwitchStatementWithDefault;
import com.shapesecurity.shift.ast.statement.ThrowStatement;
import com.shapesecurity.shift.ast.statement.TryCatchStatement;
import com.shapesecurity.shift.ast.statement.TryFinallyStatement;
import com.shapesecurity.shift.ast.statement.VariableDeclarationStatement;
import com.shapesecurity.shift.ast.statement.WhileStatement;
import com.shapesecurity.shift.ast.statement.WithStatement;
import com.shapesecurity.shift.parser.token.IdentifierLikeToken;
import com.shapesecurity.shift.parser.token.IdentifierToken;
import com.shapesecurity.shift.parser.token.NumericLiteralToken;
import com.shapesecurity.shift.parser.token.StringLiteralToken;
import com.shapesecurity.shift.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Parser extends Tokenizer {
  @NotNull
  private HashSet<String> labelSet = new HashSet<>();
  private boolean inIteration;
  private boolean inSwitch;
  private boolean inFunctionBody;
  private boolean allowIn = true;

  private Parser(@NotNull String source) throws JsError {
    super(true, source);
  }

  @Nullable
  private static PrefixOperator lookupPrefixOperator(@NotNull TokenType type) {
    switch (type) {
    case INC:
      return PrefixOperator.Increment;
    case DEC:
      return PrefixOperator.Decrement;
    case ADD:
      return PrefixOperator.Plus;
    case SUB:
      return PrefixOperator.Minus;
    case BIT_NOT:
      return PrefixOperator.BitNot;
    case NOT:
      return PrefixOperator.LogicalNot;
    case DELETE:
      return PrefixOperator.Delete;
    case VOID:
      return PrefixOperator.Void;
    case TYPEOF:
      return PrefixOperator.Typeof;
    default:
      return null;
    }
  }

  @Nullable
  private static PostfixOperator lookupPostfixOperator(@NotNull TokenType type) {
    switch (type) {
    case INC:
      return PostfixOperator.Increment;
    case DEC:
      return PostfixOperator.Decrement;
    default:
      return null;
    }
  }

  private static boolean isLeftHandSide(@NotNull Expression expr) {
    return expr instanceof MemberExpression || expr instanceof IdentifierExpression;
  }

  @NotNull
  public static Script parse(@NotNull String text) throws JsError {
    return new Parser(text).parse();
  }

  @NotNull
  public static Script parseWithLocation(@NotNull String text) throws JsError {
    return new Parser(text) {
      @NotNull
      @Override
      protected <T extends Node> T markLocation(@NotNull T node, @NotNull SourceLocation startLocation) {
        int start = startLocation.offset;
        ((Located) node).setLoc(startLocation.withSourceRange(this.getSliceBeforeLookahead(start)));
        return node;
      }
    }.parse();
  }

  @NotNull
  Token expect(@NotNull TokenType subType) throws JsError {
    if (this.lookahead.type != subType) {
      throw this.createUnexpected(this.lookahead);
    }
    return this.lex();
  }

  private boolean match(@NotNull TokenType subType) {
    return this.lookahead.type == subType;
  }

  private void consumeSemicolon() throws JsError {
    // Catch the very common case first: immediately a semicolon (U+003B).
    if (this.hasLineTerminatorBeforeNext) {
      return;
    }

    if (this.match(TokenType.SEMICOLON)) {
      this.lex();
      return;
    }

    if (!this.eof() && !this.match(TokenType.RBRACE)) {
      throw this.createUnexpected(this.lookahead);
    }
  }

  @NotNull
  protected <T extends Node> T markLocation(@NotNull T node, @NotNull SourceLocation startLocation) {
    return node;
  }

  @NotNull
  private List<Directive> parseDirective(
      @NotNull Statement[] sourceElements,
      @Nullable SourceLocation firstRestricted) throws JsError {

    if (this.lookahead.type != TokenType.STRING) {
      return List.nil();
    }

    Token token = this.lookahead;
    SourceLocation startLocation = this.getLocation();
    Statement stmt = this.parseSourceElement();
    if (stmt instanceof ExpressionStatement) {
      Expression expr = ((ExpressionStatement) stmt).expression;
      if (expr instanceof LiteralStringExpression) {
        CharSequence value = ((LiteralStringExpression) expr).raw;
        String directive = token.slice.toString();
        if ("\"use strict\"".equals(directive) || "'use strict'".equals(directive)) {
          this.strict = true;
          if (firstRestricted != null) {
            throw this.createErrorWithToken(firstRestricted, STRICT_OCTAL_LITERAL);
          }
          return List.cons(new UseStrictDirective(), this.parseDirective(sourceElements, null));
        } else {
          if (firstRestricted == null && token.octal) {
            firstRestricted = startLocation;
          }
          CharSequence content = value.subSequence(1, value.length() - 1);
          return List.cons(new UnknownDirective(content), this.parseDirective(sourceElements, firstRestricted));
        }
      }
    }
    sourceElements[0] = stmt;
    return List.nil();
  }

  @NotNull
  Script parse() throws JsError {
    SourceLocation startLocation = this.getLocation();
    this.strict = false;

    FunctionBody body = this.parseProgramBody();

    return this.markLocation(new Script(body), startLocation);
  }

  @NotNull
  private FunctionBody parseProgramBody() throws JsError {
    SourceLocation startLocation = this.getLocation();

    Statement[] firstStatement = new Statement[1];
    List<Directive> directives = this.parseDirective(firstStatement, null);

    List<Statement> statements = this.parseSourceElements();
    if (firstStatement[0] != null) {
      statements = List.cons(firstStatement[0], statements);
    }

    return this.markLocation(new FunctionBody(directives, statements), startLocation);
  }

  @NotNull
  private FunctionBody parseFunctionBody() throws JsError {
    boolean previousStrict = this.strict;
    SourceLocation startLocation = this.getLocation();

    this.expect(TokenType.LBRACE);

    Statement[] firstStatement = new Statement[1];
    List<Directive> directives = this.parseDirective(firstStatement, null);
    HashSet<String> oldLabelSet = this.labelSet;
    boolean oldInIteration = this.inIteration;
    boolean oldInSwitch = this.inSwitch;
    boolean oldInFunctionBody = this.inFunctionBody;

    this.labelSet = new HashSet<>();
    this.inIteration = false;
    this.inSwitch = false;
    this.inFunctionBody = true;

    List<Statement> statements = this.parseSourceElementsInFunctionBody();
    if (firstStatement[0] != null) {
      statements = List.cons(firstStatement[0], statements);
    }

    this.expect(TokenType.RBRACE);
    FunctionBody body = this.markLocation(new FunctionBody(directives, statements), startLocation);

    this.labelSet = oldLabelSet;
    this.inIteration = oldInIteration;
    this.inSwitch = oldInSwitch;
    this.inFunctionBody = oldInFunctionBody;
    this.strict = previousStrict;
    return body;
  }

  @NotNull
  private List<Statement> parseSourceElements() throws JsError {
    if (this.eof()) {
      return List.nil();
    }
    return List.cons(this.parseSourceElement(), this.parseSourceElements());
  }

  @NotNull
  private List<Statement> parseSourceElementsInFunctionBody() throws JsError {
    if (this.eof() || this.match(TokenType.RBRACE)) {
      return List.nil();
    }
    return List.cons(this.parseSourceElement(), this.parseSourceElementsInFunctionBody());
  }

  @NotNull
  private Statement parseSourceElement() throws JsError {
    if (this.lookahead.type.klass == TokenClass.Keyword) {
      switch (this.lookahead.type) {
      case CONST:
        return this.parseConstLetDeclaration(VariableDeclarationKind.Const);
      case LET:
        return this.parseConstLetDeclaration(VariableDeclarationKind.Let);
      case FUNCTION:
        return this.parseFunctionDeclaration();
      default:
        return this.parseStatement();
      }
    }

    return this.parseStatement();
  }

  @NotNull
  private VariableDeclarationStatement parseConstLetDeclaration(@NotNull VariableDeclarationKind kind) throws JsError {
    SourceLocation startLocation = this.getLocation();

    switch (kind) {
    case Const:
      this.expect(TokenType.CONST);
      break;
    case Let:
      this.expect(TokenType.LET);
      break;
    default:
      break;
    }

    NonEmptyList<VariableDeclarator> declarations = this.parseVariableDeclaratorList(kind);
    this.consumeSemicolon();

    return this.markLocation(new VariableDeclarationStatement(new VariableDeclaration(kind, declarations)),
        startLocation);
  }

  @NotNull
  private VariableDeclarationStatement parseVariableDeclarationStatement() throws JsError {
    SourceLocation startLocation = this.getLocation();
    this.expect(TokenType.VAR);
    NonEmptyList<VariableDeclarator> declarators = this.parseVariableDeclaratorList(VariableDeclarationKind.Var);
    this.consumeSemicolon();

    return this.markLocation(new VariableDeclarationStatement(new VariableDeclaration(VariableDeclarationKind.Var,
        declarators)), startLocation);
  }

  @NotNull
  private VariableDeclaration parseForVariableDeclaration() throws JsError {
    SourceLocation startLocation = this.getLocation();
    Token token = this.lex();

    // Preceded by this.match(TokenSubType.VAR) || this.match(TokenSubType.LET);
    VariableDeclarationKind kind =
        token.type == TokenType.VAR ? VariableDeclarationKind.Var : VariableDeclarationKind.Let;
    NonEmptyList<VariableDeclarator> declarators = this.parseVariableDeclaratorList(kind);
    return this.markLocation(new VariableDeclaration(kind, declarators), startLocation);
  }

  @NotNull
  private ParamsInfo parseParams(@Nullable SourceLocation firstRestricted) throws JsError {
    ParamsInfo info = new ParamsInfo();
    info.firstRestricted = firstRestricted;
    this.expect(TokenType.LPAREN);

    if (!this.match(TokenType.RPAREN)) {
      HashSet<String> paramSet = new HashSet<>();

      while (!this.eof()) {
        Token token = this.lookahead;
        SourceLocation location = this.getLocation();
        Identifier param = this.parseVariableIdentifier();
        String key = param.name;
        if (this.strict) {
          if (token instanceof IdentifierLikeToken && Utils.isRestrictedWord(param.name)) {
            info.stricted = location;
            info.message = STRICT_PARAM_NAME;
          }
          if (paramSet.contains(key)) {
            info.stricted = location;
            info.message = STRICT_PARAM_DUPE;
          }
        } else if (info.firstRestricted == null) {
          if (token instanceof IdentifierLikeToken && Utils.isRestrictedWord(param.name)) {
            info.firstRestricted = location;
            info.message = STRICT_PARAM_NAME;
          } else if (STRICT_MODE_RESERVED_WORD.contains(key)) {
            info.firstRestricted = location;
            info.message = STRICT_RESERVED_WORD;
          } else if (paramSet.contains(key)) {
            info.firstRestricted = location;
            info.message = STRICT_PARAM_DUPE;
          }
        }
        info.params.add(param);
        paramSet.add(key);
        if (this.match(TokenType.RPAREN)) {
          break;
        }
        this.expect(TokenType.COMMA);
      }
    }

    this.expect(TokenType.RPAREN);
    return info;
  }

  @NotNull
  private FunctionDeclaration parseFunctionDeclaration() throws JsError {
    SourceLocation startLocation = this.getLocation();
    this.expect(TokenType.FUNCTION);

    Token token = this.lookahead;
    SourceLocation location = this.getLocation();
    Identifier id = this.parseVariableIdentifier();
    SourceLocation firstRestricted = null;
    String message = null;
    if (this.strict) {
      if (token instanceof IdentifierLikeToken && Utils.isRestrictedWord(id.name)) {
        throw this.createErrorWithToken(startLocation, STRICT_FUNCTION_NAME);
      }
    } else {
      if (token instanceof IdentifierLikeToken && Utils.isRestrictedWord(id.name)) {
        firstRestricted = location;
        message = STRICT_FUNCTION_NAME;
      } else if (STRICT_MODE_RESERVED_WORD.contains(id.name)) {
        firstRestricted = location;
        message = STRICT_RESERVED_WORD;
      }
    }

    ParamsInfo info = this.parseParams(firstRestricted);
    if (info.message != null) {
      message = info.message;
      firstRestricted = info.firstRestricted;
    }

    boolean previousStrict = this.strict;
    FunctionBody body = this.parseFunctionBody();
    if ((this.strict || body.isStrict()) && firstRestricted != null) {
      throw this.createError(message, firstRestricted);
    }
    if ((this.strict || body.isStrict()) && info.stricted != null && message != null) {
      throw this.createError(message, info.stricted);
    }
    this.strict = previousStrict;

    return this.markLocation(new FunctionDeclaration(id, List.from(info.params), body), startLocation);
  }

  @NotNull
  private Statement parseStatement() throws JsError {
    if (this.eof()) {
      throw this.createUnexpected(this.lookahead);
    }

    switch (this.lookahead.type) {
    case SEMICOLON:
      return this.parseEmptyStatement();
    case LBRACE:
      return this.parseBlockStatement();
    case LPAREN:
      return this.parseExpressionStatement();
    case BREAK:
      return this.parseBreakStatement();
    case CONTINUE:
      return this.parseContinueStatement();
    case DEBUGGER:
      return this.parseDebuggerStatement();
    case DO:
      return this.parseDoWhileStatement();
    case FOR:
      return this.parseForStatement();
    case FUNCTION:
      return this.parseFunctionDeclaration();
    case IF:
      return this.parseIfStatement();
    case RETURN:
      return this.parseReturnStatement();
    case SWITCH:
      return this.parseSwitchStatement();
    case THROW:
      return this.parseThrowStatement();
    case TRY:
      return this.parseTryStatement();
    case VAR:
      return this.parseVariableDeclarationStatement();
    case WHILE:
      return this.parseWhileStatement();
    case WITH:
      return this.parseWithStatement();
    default:
      SourceLocation startLocation = this.getLocation();
      Expression expr = this.parseExpression();

      // 12.12 Labelled Statements;
      if (expr instanceof IdentifierExpression && this.match(TokenType.COLON)) {
        this.lex();
        IdentifierExpression ident = (IdentifierExpression) expr;
        String key = ident.identifier.name;
        if (this.labelSet.contains(key)) {
          throw this.createError(LABEL_REDECLARATION, ident.identifier.name);
        }

        this.labelSet.add(key);
        Statement labeledBody = this.parseStatement();
        this.labelSet.remove(key);
        return this.markLocation(new LabeledStatement(ident.identifier, labeledBody), startLocation);
      } else {
        this.consumeSemicolon();
        return this.markLocation(new ExpressionStatement(expr), startLocation);
      }
    }
  }

  private BlockStatement parseBlockStatement() throws JsError {
    SourceLocation startLocation = this.getLocation();
    return this.markLocation(new BlockStatement(this.parseBlock()), startLocation);
  }

  // guaranteed to parse at least one declarator
  @NotNull
  private NonEmptyList<VariableDeclarator> parseVariableDeclaratorList(VariableDeclarationKind kind) throws JsError {
    VariableDeclarator variableDeclarator = this.parseVariableDeclarator(kind);
    if (!this.match(TokenType.COMMA)) {
      return List.list(variableDeclarator);
    }
    this.lex();
    if (this.eof()) {
      return List.list(variableDeclarator);
    }
    return List.cons(variableDeclarator, this.parseVariableDeclaratorList(kind));
  }

  @NotNull
  private Identifier parseVariableIdentifier() throws JsError {
    SourceLocation startLocation = this.getLocation();

    Token token = this.lex();
    if (!(token instanceof IdentifierToken)) {
      throw this.createUnexpected(token);
    }

    return this.markLocation(new Identifier(String.valueOf(token.getValueString())), startLocation);
  }

  @NotNull
  private EmptyStatement parseEmptyStatement() throws JsError {
    SourceLocation startLocation = this.getLocation();
    this.expect(TokenType.SEMICOLON);
    return this.markLocation(new EmptyStatement(), startLocation);
  }

  @NotNull
  private Block parseBlock() throws JsError {
    SourceLocation startLocation = this.getLocation();
    this.expect(TokenType.LBRACE);

    List<Statement> body = this.parseStatementList();

    this.expect(TokenType.RBRACE);

    return this.markLocation(new Block(body), startLocation);
  }

  @NotNull
  private ExpressionStatement parseExpressionStatement() throws JsError {
    SourceLocation startLocation = this.getLocation();
    Expression expr = this.parseExpression();
    this.consumeSemicolon();
    return this.markLocation(new ExpressionStatement(expr), startLocation);
  }

  @NotNull
  private BreakStatement parseBreakStatement() throws JsError {
    SourceLocation startLocation = this.getLocation();
    this.expect(TokenType.BREAK);

    // Catch the very common case first: immediately a semicolon (U+003B).
    if (this.lookahead.type == TokenType.SEMICOLON) {
      this.lex();

      if (!(this.inIteration || this.inSwitch)) {
        throw this.createErrorWithToken(startLocation, ILLEGAL_BREAK);
      }

      return this.markLocation(new BreakStatement(Maybe.<Identifier>nothing()), startLocation);
    }

    if (this.hasLineTerminatorBeforeNext) {
      if (!(this.inIteration || this.inSwitch)) {
        throw this.createErrorWithToken(startLocation, ILLEGAL_BREAK);
      }

      return this.markLocation(new BreakStatement(Maybe.<Identifier>nothing()), startLocation);
    }

    Identifier label = null;
    if (this.lookahead.type == TokenType.IDENTIFIER) {
      label = this.parseVariableIdentifier();

      String key = label.name;
      if (!this.labelSet.contains(key)) {
        throw this.createError(UNKNOWN_LABEL, label.name);
      }
    }

    this.consumeSemicolon();

    if (label == null && !(this.inIteration || this.inSwitch)) {
      throw this.createErrorWithToken(startLocation, ILLEGAL_BREAK);
    }

    return this.markLocation(new BreakStatement(Maybe.fromNullable(label)), startLocation);
  }

  @NotNull
  private ContinueStatement parseContinueStatement() throws JsError {
    SourceLocation startLocation = this.getLocation();
    this.expect(TokenType.CONTINUE);

    // Catch the very common case first: immediately a semicolon (U+003B).
    if (this.lookahead.type == TokenType.SEMICOLON) {
      this.lex();
      if (!this.inIteration) {
        throw this.createErrorWithToken(startLocation, ILLEGAL_CONTINUE);
      }

      return this.markLocation(new ContinueStatement(Maybe.<Identifier>nothing()), startLocation);
    }

    if (this.hasLineTerminatorBeforeNext) {
      if (!this.inIteration) {
        throw this.createErrorWithToken(startLocation, ILLEGAL_CONTINUE);
      }

      return this.markLocation(new ContinueStatement(Maybe.<Identifier>nothing()), startLocation);
    }

    Identifier label = null;
    if (this.lookahead.type == TokenType.IDENTIFIER) {
      label = this.parseVariableIdentifier();

      String key = label.name;
      if (!this.labelSet.contains(key)) {
        throw this.createError(UNKNOWN_LABEL, label.name);
      }
    }

    this.consumeSemicolon();
    if (!this.inIteration) {
      throw this.createErrorWithToken(startLocation, ILLEGAL_CONTINUE);
    }

    return this.markLocation(new ContinueStatement(Maybe.fromNullable(label)), startLocation);
  }

  @NotNull
  private DebuggerStatement parseDebuggerStatement() throws JsError {
    SourceLocation startLocation = this.getLocation();
    this.expect(TokenType.DEBUGGER);
    this.consumeSemicolon();
    return this.markLocation(new DebuggerStatement(), startLocation);
  }

  @NotNull
  private DoWhileStatement parseDoWhileStatement() throws JsError {
    SourceLocation startLocation = this.getLocation();
    this.expect(TokenType.DO);
    boolean oldInIteration = this.inIteration;
    this.inIteration = true;

    Statement body = this.parseStatement();
    this.inIteration = oldInIteration;

    this.expect(TokenType.WHILE);
    this.expect(TokenType.LPAREN);
    Expression test = this.parseExpression();
    this.expect(TokenType.RPAREN);
    if (this.match(TokenType.SEMICOLON)) {
      this.lex();
    }

    return this.markLocation(new DoWhileStatement(body, test), startLocation);
  }

  @NotNull
  private Statement parseForStatement() throws JsError {
    SourceLocation startLocation = this.getLocation();

    this.expect(TokenType.FOR);
    this.expect(TokenType.LPAREN);
    Expression test = null;
    Expression right = null;
    if (this.match(TokenType.SEMICOLON)) {
      this.lex();
      if (!this.match(TokenType.SEMICOLON)) {
        test = this.parseExpression();
      }
      this.expect(TokenType.SEMICOLON);
      if (!this.match(TokenType.RPAREN)) {
        right = this.parseExpression();
      }
      return this.markLocation(new ForStatement(Maybe.nothing(), Maybe.fromNullable(test), Maybe.fromNullable(right),
          this.getIteratorStatementEpilogue()), startLocation);
    } else {
      if (this.match(TokenType.VAR) || this.match(TokenType.LET)) {
        boolean previousAllowIn = this.allowIn;
        this.allowIn = false;
        VariableDeclaration initDecl = this.parseForVariableDeclaration();
        this.allowIn = previousAllowIn;

        if (initDecl.declarators.tail().isEmpty() && this.match(TokenType.IN)) {
          this.lex();
          right = this.parseExpression();
          return this.markLocation(new ForInStatement(initDecl, right, this.getIteratorStatementEpilogue()),
              startLocation);
        } else {
          this.expect(TokenType.SEMICOLON);
          if (!this.match(TokenType.SEMICOLON)) {
            test = this.parseExpression();
          }
          this.expect(TokenType.SEMICOLON);
          if (!this.match(TokenType.RPAREN)) {
            right = this.parseExpression();
          }
          return this.markLocation(new ForStatement(initDecl, Maybe.fromNullable(test), Maybe.fromNullable(right),
              this.getIteratorStatementEpilogue()), startLocation);
        }
      } else {
        boolean previousAllowIn = this.allowIn;
        this.allowIn = false;
        Expression init = this.parseExpression();
        this.allowIn = previousAllowIn;

        if (this.match(TokenType.IN)) {
          // LeftHandSideExpression;
          if (!(init instanceof LeftHandSideExpression)) {
            throw this.createError(INVALID_LHS_IN_FOR_IN);
          }

          this.lex();
          right = this.parseExpression();
          return this.markLocation(new ForInStatement(init, right, this.getIteratorStatementEpilogue()),
              startLocation);
        } else {
          this.expect(TokenType.SEMICOLON);
          if (!this.match(TokenType.SEMICOLON)) {
            test = this.parseExpression();
          }
          this.expect(TokenType.SEMICOLON);
          if (!this.match(TokenType.RPAREN)) {
            right = this.parseExpression();
          }
          return this.markLocation(new ForStatement(Maybe.fromNullable(init).map(Either::right), Maybe.fromNullable(
              test), Maybe.fromNullable(right), this.getIteratorStatementEpilogue()), startLocation);
        }
      }
    }
  }

  private Statement getIteratorStatementEpilogue() throws JsError {
    this.expect(TokenType.RPAREN);
    boolean oldInIteration = this.inIteration;
    this.inIteration = true;
    Statement body = this.parseStatement();
    this.inIteration = oldInIteration;
    return body;
  }

  @NotNull
  private IfStatement parseIfStatement() throws JsError {
    SourceLocation startLocation = this.getLocation();
    this.expect(TokenType.IF);
    this.expect(TokenType.LPAREN);
    Expression test = this.parseExpression();

    this.expect(TokenType.RPAREN);
    Statement consequent = this.parseStatement();
    Maybe<Statement> alternate;
    if (this.match(TokenType.ELSE)) {
      this.lex();
      alternate = Maybe.fromNullable(this.parseStatement());
    } else {
      alternate = Maybe.nothing();
    }

    return this.markLocation(new IfStatement(test, consequent, alternate), startLocation);
  }

  @NotNull
  private ReturnStatement parseReturnStatement() throws JsError {
    SourceLocation startLocation = this.getLocation();
    Maybe<Expression> argument = Maybe.nothing();

    this.expect(TokenType.RETURN);
    if (!this.inFunctionBody) {
      throw this.createError(ILLEGAL_RETURN);
    }

    if (this.hasLineTerminatorBeforeNext) {
      return this.markLocation(new ReturnStatement(Maybe.<Expression>nothing()), startLocation);
    }

    if (!this.match(TokenType.SEMICOLON)) {
      if (!this.match(TokenType.RBRACE) && !this.eof()) {
        argument = Maybe.fromNullable(this.parseExpression());
      }
    }

    this.consumeSemicolon();
    return this.markLocation(new ReturnStatement(argument), startLocation);
  }

  @NotNull
  private WithStatement parseWithStatement() throws JsError {
    SourceLocation startLocation = this.getLocation();
    if (this.strict) {
      throw this.createError(STRICT_MODE_WITH);
    }

    this.expect(TokenType.WITH);
    this.expect(TokenType.LPAREN);
    Expression object = this.parseExpression();
    this.expect(TokenType.RPAREN);
    Statement body = this.parseStatement();

    return this.markLocation(new WithStatement(object, body), startLocation);
  }

  @NotNull
  private Statement parseSwitchStatement() throws JsError {
    SourceLocation startLocation = this.getLocation();

    this.expect(TokenType.SWITCH);
    this.expect(TokenType.LPAREN);

    Expression discriminant = this.parseExpression();
    this.expect(TokenType.RPAREN);
    this.expect(TokenType.LBRACE);

    if (this.match(TokenType.RBRACE)) {
      this.lex();
      return this.markLocation(new SwitchStatement(discriminant, List.<SwitchCase>nil()), startLocation);
    }
    boolean oldInSwitch = this.inSwitch;
    this.inSwitch = true;

    List<SwitchCase> cases = this.parseSwitchCases();

    if (this.match(TokenType.DEFAULT)) {
      SwitchDefault switchDefault = this.parseSwitchDefault();
      List<SwitchCase> postDefaultCases = this.parseSwitchCases();
      if (this.match(TokenType.DEFAULT)) {
        throw this.createError(MULTIPLE_DEFAULTS_IN_SWITCH);
      }
      this.inSwitch = oldInSwitch;
      this.expect(TokenType.RBRACE);
      return this.markLocation(new SwitchStatementWithDefault(discriminant, cases, switchDefault, postDefaultCases),
          startLocation);
    } else {
      this.inSwitch = oldInSwitch;
      this.expect(TokenType.RBRACE);
      return this.markLocation(new SwitchStatement(discriminant, cases), startLocation);
    }
  }

  private List<SwitchCase> parseSwitchCases() throws JsError {
    if (this.eof() || this.match(TokenType.RBRACE) || this.match(TokenType.DEFAULT)) {
      return List.nil();
    }
    return List.cons(this.parseSwitchCase(), this.parseSwitchCases());
  }

  @NotNull
  private ThrowStatement parseThrowStatement() throws JsError {
    SourceLocation startLocation = this.getLocation();

    this.expect(TokenType.THROW);

    if (this.hasLineTerminatorBeforeNext) {
      throw this.createErrorWithToken(startLocation, NEWLINE_AFTER_THROW);
    }

    Expression argument = this.parseExpression();

    this.consumeSemicolon();

    return this.markLocation(new ThrowStatement(argument), startLocation);
  }

  @NotNull
  private Statement parseTryStatement() throws JsError {
    SourceLocation startLocation = this.getLocation();
    this.expect(TokenType.TRY);
    Block block = this.parseBlock();

    if (this.match(TokenType.CATCH)) {
      CatchClause handler = this.parseCatchClause();
      if (this.match(TokenType.FINALLY)) {
        this.lex();
        Block finalizer = this.parseBlock();
        return this.markLocation(new TryFinallyStatement(block, Maybe.just(handler), finalizer), startLocation);
      }
      return this.markLocation(new TryCatchStatement(block, handler), startLocation);
    }

    if (this.match(TokenType.FINALLY)) {
      this.lex();
      Block finalizer = this.parseBlock();
      return this.markLocation(new TryFinallyStatement(block, Maybe.<CatchClause>nothing(), finalizer),
          startLocation);
    } else {
      throw this.createError(NO_CATCH_OR_FINALLY);
    }
  }

  @NotNull
  private WhileStatement parseWhileStatement() throws JsError {
    SourceLocation startLocation = this.getLocation();
    this.expect(TokenType.WHILE);
    this.expect(TokenType.LPAREN);
    return this.markLocation(new WhileStatement(this.parseExpression(), this.getIteratorStatementEpilogue()),
        startLocation);
  }

  private Expression parseExpression() throws JsError {
    SourceLocation startLocation = this.getLocation();

    Expression expr = this.parseAssignmentExpression();

    if (this.match(TokenType.COMMA)) {
      while (!this.eof()) {
        if (!this.match(TokenType.COMMA)) {
          break;
        }
        this.lex();
        expr = this.markLocation(new BinaryExpression(BinaryOperator.Sequence, expr, this.parseAssignmentExpression()),
            startLocation);
      }
    }

    return expr;
  }

  @NotNull
  private VariableDeclarator parseVariableDeclarator(VariableDeclarationKind kind) throws JsError {
    SourceLocation startLocation = this.getLocation();

    Identifier id = this.parseVariableIdentifier();

    // 12.2.1;
    if (this.strict && Utils.isRestrictedWord(id.name)) {
      throw this.createError(STRICT_VAR_NAME);
    }

    Maybe<Expression> init = Maybe.nothing();
    if (kind == VariableDeclarationKind.Const) {
      this.expect(TokenType.ASSIGN);
      init = Maybe.just(this.parseAssignmentExpression());
    } else if (this.match(TokenType.ASSIGN)) {
      this.lex();
      init = Maybe.just(this.parseAssignmentExpression());
    }
    return this.markLocation(new VariableDeclarator(id, init), startLocation);
  }

  @NotNull
  // ECMAScript 5 does not allow FunctionDeclarations in block statements, but no
  // implementations comply to this restriction.
  private List<Statement> parseStatementList() throws JsError {
    if (this.eof()) {
      return List.nil();
    }

    if (this.match(TokenType.RBRACE)) {
      return List.nil();
    }
    return List.cons(this.parseSourceElement(), this.parseStatementList());
  }

  @NotNull
  private SwitchCase parseSwitchCase() throws JsError {
    SourceLocation startLocation = this.getLocation();
    this.expect(TokenType.CASE);
    return this.markLocation(new SwitchCase(this.parseExpression(), this.parseSwitchCaseBody()), startLocation);
  }

  @NotNull
  private SwitchDefault parseSwitchDefault() throws JsError {
    SourceLocation startLocation = this.getLocation();
    this.expect(TokenType.DEFAULT);
    return this.markLocation(new SwitchDefault(this.parseSwitchCaseBody()), startLocation);
  }

  private List<Statement> parseSwitchCaseBody() throws JsError {
    this.expect(TokenType.COLON);
    return this.parseStatementListInSwitchCaseBody();
  }

  private List<Statement> parseStatementListInSwitchCaseBody() throws JsError {
    if (this.eof() || this.match(TokenType.RBRACE) || this.match(TokenType.DEFAULT) || this.match(TokenType.CASE)) {
      return List.nil();
    }
    return List.cons(this.parseSourceElement(), this.parseStatementListInSwitchCaseBody());
  }

  @NotNull
  private CatchClause parseCatchClause() throws JsError {
    SourceLocation startLocation = this.getLocation();

    this.expect(TokenType.CATCH);
    this.expect(TokenType.LPAREN);
    if (this.match(TokenType.RPAREN)) {
      throw this.createUnexpected(this.lookahead);
    }

    Identifier param = this.parseVariableIdentifier();

    // 12.14.1;
    if (this.strict && Utils.isRestrictedWord(param.name)) {
      throw this.createError(STRICT_CATCH_VARIABLE);
    }

    this.expect(TokenType.RPAREN);

    Block body = this.parseBlock();

    return this.markLocation(new CatchClause(param, body), startLocation);
  }

  @NotNull
  private Expression parseAssignmentExpression() throws JsError {
    SourceLocation startLocation = this.getLocation();

    Expression node = this.parseConditionalExpression();

    Assignment operator = null;
    switch (this.lookahead.type) {
    case ASSIGN:
      operator = Assignment.Assign;
      break;
    case ASSIGN_BIT_OR:
      operator = Assignment.AssignBitOr;
      break;
    case ASSIGN_BIT_XOR:
      operator = Assignment.AssignBitXor;
      break;
    case ASSIGN_BIT_AND:
      operator = Assignment.AssignBitAnd;
      break;
    case ASSIGN_SHL:
      operator = Assignment.AssignLeftShift;
      break;
    case ASSIGN_SHR:
      operator = Assignment.AssignRightShift;
      break;
    case ASSIGN_SHR_UNSIGNED:
      operator = Assignment.AssignUnsignedRightShift;
      break;
    case ASSIGN_ADD:
      operator = Assignment.AssignPlus;
      break;
    case ASSIGN_SUB:
      operator = Assignment.AssignMinus;
      break;
    case ASSIGN_MUL:
      operator = Assignment.AssignMul;
      break;
    case ASSIGN_DIV:
      operator = Assignment.AssignDiv;
      break;
    case ASSIGN_MOD:
      operator = Assignment.AssignRem;
      break;
    default:
      break;
    }

    if (operator != null) {
      // To be permissive.
      // if (!isLeftHandSide(node)) {
      //     throw this.createError(INVALID_LHS_IN_ASSIGNMENT);
      // }

      // 11.13.1;
      if (node instanceof IdentifierExpression) {
        IdentifierExpression ident = (IdentifierExpression) node;
        if (this.strict && Utils.isRestrictedWord(ident.identifier.name)) {
          throw this.createErrorWithToken(startLocation, STRICT_LHS_ASSIGNMENT);
        }
      }

      this.lex();
      Expression right = this.parseAssignmentExpression();
      return this.markLocation(new AssignmentExpression(operator, node, right), startLocation);
    }
    return node;
  }

  @NotNull
  private Expression parseConditionalExpression() throws JsError {
    SourceLocation startLocation = this.getLocation();
    Expression expr = this.parseBinaryExpression();
    if (this.match(TokenType.CONDITIONAL)) {
      this.lex();
      boolean previousAllowIn = this.allowIn;
      this.allowIn = true;
      Expression consequent = this.parseAssignmentExpression();
      this.allowIn = previousAllowIn;
      this.expect(TokenType.COLON);
      Expression alternate = this.parseAssignmentExpression();
      return this.markLocation(new ConditionalExpression(expr, consequent, alternate), startLocation);
    }

    return expr;
  }

  @Nullable
  private BinaryOperator lookupBinaryOperator(@NotNull TokenType type) {
    switch (type) {
    case OR:
      return BinaryOperator.LogicalOr;
    case AND:
      return BinaryOperator.LogicalAnd;
    case BIT_OR:
      return BinaryOperator.BitwiseOr;
    case BIT_XOR:
      return BinaryOperator.BitwiseXor;
    case BIT_AND:
      return BinaryOperator.BitwiseAnd;
    case EQ:
      return BinaryOperator.Equal;
    case NE:
      return BinaryOperator.NotEqual;
    case EQ_STRICT:
      return BinaryOperator.StrictEqual;
    case NE_STRICT:
      return BinaryOperator.StrictNotEqual;
    case LT:
      return BinaryOperator.LessThan;
    case GT:
      return BinaryOperator.GreaterThan;
    case LTE:
      return BinaryOperator.LessThanEqual;
    case GTE:
      return BinaryOperator.GreaterThanEqual;
    case INSTANCEOF:
      return BinaryOperator.Instanceof;
    case IN:
      return this.allowIn ? BinaryOperator.In : null;
    case SHL:
      return BinaryOperator.Left;
    case SHR:
      return BinaryOperator.Right;
    case SHR_UNSIGNED:
      return BinaryOperator.UnsignedRight;
    case ADD:
      return BinaryOperator.Plus;
    case SUB:
      return BinaryOperator.Minus;
    case MUL:
      return BinaryOperator.Mul;
    case DIV:
      return BinaryOperator.Div;
    case MOD:
      return BinaryOperator.Rem;
    default:
      return null;
    }
  }

  @NotNull
  private Expression parseBinaryExpression() throws JsError {
    Expression left = this.parseUnaryExpression();
    Token token = this.lookahead;
    BinaryOperator operator = this.lookupBinaryOperator(token.type);
    if (operator == null) {
      return left;
    }

    this.lex();

    List<ExprStackItem> stack = List.nil();
    stack = stack.cons(new ExprStackItem(this.getLocation(), left, operator));
    Expression expr = this.parseUnaryExpression();

    operator = this.lookupBinaryOperator(this.lookahead.type);
    while (operator != null) {
      Precedence precedence = operator.getPrecedence();
      // Reduce: make a binary expression from the three topmost entries.
      while ((stack.isNotEmpty()) && (precedence.ordinal() <= ((NonEmptyList<ExprStackItem>) stack).head.precedence)) {
        ExprStackItem stackItem = ((NonEmptyList<ExprStackItem>) stack).head;
        BinaryOperator stackOperator = stackItem.operator;
        left = stackItem.left;
        stack = ((NonEmptyList<ExprStackItem>) stack).tail();
        expr = this.markLocation(new BinaryExpression(stackOperator, left, expr), stackItem.startLocation);
      }

      // Shift.
      this.lex();
      stack = stack.cons(new ExprStackItem(this.getLocation(), expr, operator));
      expr = this.parseUnaryExpression();

      operator = this.lookupBinaryOperator(this.lookahead.type);
    }

    // Final reduce to clean-up the stack.
    return stack.foldLeft((expr1, stackItem) -> this.markLocation(new BinaryExpression(stackItem.operator,
        stackItem.left,
        expr1), stackItem.startLocation), expr);
  }

  @NotNull
  private Expression parseUnaryExpression() throws JsError {
    if (this.lookahead.type.klass != TokenClass.Punctuator && this.lookahead.type.klass != TokenClass.Keyword) {
      return this.parsePostfixExpression();
    }
    SourceLocation startLocation = this.getLocation();
    PrefixOperator operator = lookupPrefixOperator(this.lookahead.type);
    if (operator == null) {
      return this.parsePostfixExpression();
    }
    this.lex();
    Expression expr = this.parseUnaryExpression();
    switch (operator) {
    case Increment:
    case Decrement:
      // 11.4.4, 11.4.5;
      if (expr instanceof IdentifierExpression) {
        IdentifierExpression ident = (IdentifierExpression) expr;
        if (this.strict && Utils.isRestrictedWord(ident.identifier.name)) {
          throw this.createError(STRICT_LHS_PREFIX);
        }
      }

      if (!isLeftHandSide(expr)) {
        throw this.createError(INVALID_LHS_IN_ASSIGNMENT);
      }
      break;
    case Delete:
      if (expr instanceof IdentifierExpression && this.strict) {
        throw this.createError(STRICT_DELETE);
      }
      break;
    default:
      break;
    }

    return this.markLocation(new PrefixExpression(operator, expr), startLocation);
  }

  @NotNull
  private Expression parsePostfixExpression() throws JsError {
    SourceLocation startLocation = this.getLocation();

    Expression expr = this.parseLeftHandSideExpressionAllowCall();

    if (this.hasLineTerminatorBeforeNext) {
      return expr;
    }

    PostfixOperator operator = lookupPostfixOperator(this.lookahead.type);
    if (operator == null) {
      return expr;
    }
    this.lex();
    // 11.3.1, 11.3.2;
    if (expr instanceof IdentifierExpression) {
      IdentifierExpression ident = (IdentifierExpression) expr;
      if (this.strict && Utils.isRestrictedWord(ident.identifier.name)) {
        throw this.createError(STRICT_LHS_POSTFIX);
      }
    }
    if (!isLeftHandSide(expr)) {
      throw this.createError(INVALID_LHS_IN_ASSIGNMENT);
    }
    return this.markLocation(new PostfixExpression(operator, expr), startLocation);
  }

  @NotNull
  private Expression parseLeftHandSideExpressionAllowCall() throws JsError {
    SourceLocation startLocation = this.getLocation();
    boolean previousAllowIn = this.allowIn;
    this.allowIn = true;
    Expression expr = this.match(TokenType.NEW) ? this.parseNewExpression() : this.parsePrimaryExpression();

    while (true) {
      if (this.match(TokenType.LPAREN)) {
        expr = this.markLocation(new CallExpression(expr, this.parseArgumentList()), startLocation);
      } else if (this.match(TokenType.LBRACK)) {
        expr = this.markLocation(new ComputedMemberExpression(expr, this.parseComputedMember()), startLocation);
      } else if (this.match(TokenType.PERIOD)) {
        expr = this.markLocation(new StaticMemberExpression(expr, this.parseNonComputedMember()), startLocation);
      } else {
        break;
      }
    }

    this.allowIn = previousAllowIn;

    return expr;
  }

  @NotNull
  private Expression parseLeftHandSideExpression() throws JsError {
    SourceLocation startLocation = this.getLocation();

    assert this.allowIn;

    Expression expr = this.match(TokenType.NEW) ? this.parseNewExpression() : this.parsePrimaryExpression();

    while (this.match(TokenType.PERIOD) || this.match(TokenType.LBRACK)) {
      expr = this.match(TokenType.LBRACK) ? this.markLocation(new ComputedMemberExpression(expr,
          this.parseComputedMember()), startLocation) : this.markLocation(new StaticMemberExpression(expr,
          this.parseNonComputedMember()), startLocation);
    }

    return expr;
  }

  @NotNull
  private Identifier parseNonComputedMember() throws JsError {
    this.expect(TokenType.PERIOD);
    return this.parseNonComputedProperty();
  }

  private Expression parseComputedMember() throws JsError {
    this.expect(TokenType.LBRACK);
    Expression expr = this.parseExpression();
    this.expect(TokenType.RBRACK);
    return expr;
  }

  @NotNull
  private Expression parseNewExpression() throws JsError {
    SourceLocation startLocation = this.getLocation();
    this.expect(TokenType.NEW);
    Expression callee = this.parseLeftHandSideExpression();
    return this.markLocation(new NewExpression(callee, this.match(TokenType.LPAREN) ? this.parseArgumentList() :
        List.<Expression>nil()), startLocation);
  }

  @NotNull
  private Expression parsePrimaryExpression() throws JsError {
    if (this.match(TokenType.LPAREN)) {
      return this.parseGroupExpression();
    }

    SourceLocation startLocation = this.getLocation();

    switch (this.lookahead.type.klass) {
    case Ident:
      return this.markLocation(new IdentifierExpression(this.parseIdentifier()), startLocation);
    case StringLiteral:
      return this.parseStringLiteral();
    case NumericLiteral:
      return this.parseNumericLiteral();
    case Keyword: {
      if (this.match(TokenType.THIS)) {
        this.lex();
        return this.markLocation(new ThisExpression(), startLocation);
      }
      if (this.match(TokenType.FUNCTION)) {
        return this.parseFunctionExpression();
      }
      break;
    }
    case BooleanLiteral: {
      Token token = this.lex();
      return this.markLocation(new LiteralBooleanExpression(token.type == TokenType.TRUE_LITERAL), startLocation);
    }
    case NullLiteral: {
      this.lex();
      return this.markLocation(new LiteralNullExpression(), startLocation);
    }
    default:
      if (this.match(TokenType.LBRACK)) {
        return this.parseArrayExpression();
      } else if (this.match(TokenType.LBRACE)) {
        return this.parseObjectExpression();
      } else if (this.match(TokenType.DIV) || this.match(TokenType.ASSIGN_DIV)) {
        this.lookahead = this.rescanRegExp();
        Token token = this.lex();
        return this.markLocation(new LiteralRegExpExpression(String.valueOf(token.getValueString())), startLocation);
      }
    }

    throw this.createUnexpected(this.lex());
  }

  @NotNull
  private LiteralNumericExpression parseNumericLiteral() throws JsError {
    SourceLocation startLocation = this.getLocation();
    if (this.strict && this.lookahead.octal) {
      throw this.createError(STRICT_OCTAL_LITERAL);
    }
    Token token2 = this.lex();
    return this.markLocation(new LiteralNumericExpression(((NumericLiteralToken) token2).value), startLocation);
  }

  @NotNull
  private LiteralStringExpression parseStringLiteral() throws JsError {
    SourceLocation startLocation = this.getLocation();
    if (this.strict && this.lookahead.octal) {
      throw this.createError(STRICT_OCTAL_LITERAL);
    }
    Token token2 = this.lex();
    return this.markLocation(new LiteralStringExpression(String.valueOf(token2.getValueString()), token2.slice),
        startLocation);
  }

  @NotNull
  private Identifier parseIdentifier() throws JsError {
    SourceLocation startLocation = this.getLocation();
    return this.markLocation(new Identifier(String.valueOf(this.lex().getValueString())), startLocation);
  }

  @NotNull
  private List<Expression> parseArgumentList() throws JsError {
    this.expect(TokenType.LPAREN);
    List<Expression> args = this.parseArguments();
    this.expect(TokenType.RPAREN);
    return args;
  }

  @NotNull
  private List<Expression> parseArguments() throws JsError {
    if (this.match(TokenType.RPAREN) || this.eof()) {
      return List.nil();
    }
    Expression arg = this.parseAssignmentExpression();
    if (this.match(TokenType.COMMA)) {
      this.expect(TokenType.COMMA);
      return List.cons(arg, this.parseArguments());
    }
    return List.list(arg);
  }

  // 11.2 Left-Hand-Side Expressions;

  @NotNull
  private Identifier parseNonComputedProperty() throws JsError {
    SourceLocation startLocation = this.getLocation();

    Token token = this.lex();

    if (!(token instanceof IdentifierLikeToken)) {
      throw this.createUnexpected(token);
    } else {
      return this.markLocation(new Identifier(String.valueOf(token.getValueString())), startLocation);
    }
  }

  @NotNull
  private Expression parseGroupExpression() throws JsError {
    this.expect(TokenType.LPAREN);
    Expression expr = this.parseExpression();
    this.expect(TokenType.RPAREN);
    return expr;
  }

  @NotNull
  private FunctionExpression parseFunctionExpression() throws JsError {
    SourceLocation startLocation = this.getLocation();

    this.expect(TokenType.FUNCTION);

    Identifier id = null;
    String message = null;
    SourceLocation firstRestricted = null;
    if (!this.match(TokenType.LPAREN)) {
      Token token = this.lookahead;
      SourceLocation location = this.getLocation();
      id = this.parseVariableIdentifier();
      if (token instanceof IdentifierLikeToken) {
        if (this.strict) {
          if (Utils.isRestrictedWord(id.name)) {
            throw this.createErrorWithToken(startLocation, STRICT_FUNCTION_NAME);
          }
        } else {
          if (Utils.isRestrictedWord(id.name)) {
            firstRestricted = location;
            message = STRICT_FUNCTION_NAME;
          } else if (Utils.isStrictModeReservedWordES5(id.name)) {
            firstRestricted = location;
            message = STRICT_RESERVED_WORD;
          }
        }
      }
    }
    ParamsInfo info = this.parseParams(firstRestricted);

    if (info.message != null) {
      message = info.message;
    }

    boolean previousStrict = this.strict;
    FunctionBody body = this.parseFunctionBody();
    if (message != null) {
      if ((this.strict || body.isStrict()) && info.firstRestricted != null) {
        throw this.createErrorWithToken(info.firstRestricted, message);
      }
      if ((this.strict || body.isStrict()) && info.stricted != null) {
        throw this.createErrorWithToken(info.stricted, message);
      }
    }
    this.strict = previousStrict;
    return this.markLocation(new FunctionExpression(Maybe.fromNullable(id), List.from(info.params), body),
        startLocation);
  }

  @NotNull
  private ArrayExpression parseArrayExpression() throws JsError {
    SourceLocation startLocation = this.getLocation();

    this.expect(TokenType.LBRACK);

    List<Maybe<Expression>> elements = this.parseArrayExpressionElements();

    this.expect(TokenType.RBRACK);

    return this.markLocation(new ArrayExpression(elements), startLocation);
  }

  @NotNull
  private List<Maybe<Expression>> parseArrayExpressionElements() throws JsError {
    if (this.match(TokenType.RBRACK)) {
      return List.nil();
    }

    Maybe<Expression> el;

    if (this.match(TokenType.COMMA)) {
      this.lex();
      el = Maybe.nothing();
    } else {
      el = Maybe.just(this.parseAssignmentExpression());
      if (!this.match(TokenType.RBRACK)) {
        this.expect(TokenType.COMMA);
      }
    }
    return List.cons(el, this.parseArrayExpressionElements());
  }

  @NotNull
  private ObjectExpression parseObjectExpression() throws JsError {
    SourceLocation startLocation = this.getLocation();

    this.expect(TokenType.LBRACE);

    HashMap<String, ObjectPropertyCombination> propertyMap = new HashMap<>();
    List<ObjectProperty> properties = this.parseObjectExpressionItems(propertyMap);

    this.expect(TokenType.RBRACE);

    return this.markLocation(new ObjectExpression(properties), startLocation);
  }

  @NotNull
  private List<ObjectProperty> parseObjectExpressionItems(
      @NotNull HashMap<String, ObjectPropertyCombination> propertyMap)
      throws JsError {
    if (this.match(TokenType.RBRACE)) {
      return List.nil();
    }

    ObjectProperty property = this.parseObjectProperty();
    ObjectPropertyKind kind = property.getKind();
    @NotNull
    final String key = property.name.value;
    final ObjectPropertyCombination maybeValue = propertyMap.get(key);
    @NotNull
    final ObjectPropertyCombination value = maybeValue == null ? ObjectPropertyCombination.NIL : maybeValue;

    if (propertyMap.containsKey(key)) {
      if (value.hasInit) {
        if (this.strict && kind == ObjectPropertyKind.InitProperty) {
          throw this.createError(STRICT_DUPLICATE_PROPERTY);
        } else if (kind != ObjectPropertyKind.InitProperty) {
          throw this.createError(ACCESSOR_DATA_PROPERTY);
        }
      } else {
        if (kind == ObjectPropertyKind.InitProperty) {
          throw this.createError(ACCESSOR_DATA_PROPERTY);
        } else if (value.hasGetter && kind == ObjectPropertyKind.GetterProperty
            || value.hasSetter && kind == ObjectPropertyKind.SetterProperty) {
          throw this.createError(ACCESSOR_GET_SET);
        }
      }
    }
    switch (kind) {
    case InitProperty:
      propertyMap.put(key, value.withInit());
      break;
    case GetterProperty:
      propertyMap.put(key, value.withGetter());
      break;
    case SetterProperty:
      propertyMap.put(key, value.withSetter());
      break;
    }

    if (!this.match(TokenType.RBRACE)) {
      this.expect(TokenType.COMMA);
    }

    return List.cons(property, this.parseObjectExpressionItems(propertyMap));
  }

  @NotNull
  private PropertyName parseObjectPropertyKey() throws JsError {
    Token token = this.lookahead;

    // Note: This function is called only from parseObjectProperty(), where;
    // Eof and Punctuator tokens are already filtered out.

    if (token instanceof StringLiteralToken) {
      return new PropertyName(this.parseStringLiteral());
    }
    if (token instanceof NumericLiteralToken) {
      return new PropertyName(this.parseNumericLiteral());
    }
    if (token instanceof IdentifierLikeToken) {
      return new PropertyName(this.parseIdentifier());
    }

    throw this.createError(INVALID_PROPERTY_NAME);
  }

  @NotNull
  private ObjectProperty parseObjectProperty() throws JsError {
    Token token = this.lookahead;
    SourceLocation startLocation = this.getLocation();

    if (token.type == TokenType.IDENTIFIER) {
      PropertyName key = this.parseObjectPropertyKey();
      String name = token.toString();
      if (name.length() == 3) {
        // Property Assignment: Getter and Setter.
        if ("get".equals(name) && !this.match(TokenType.COLON)) {
          key = this.parseObjectPropertyKey();
          this.expect(TokenType.LPAREN);
          this.expect(TokenType.RPAREN);
          FunctionBody body = this.parseFunctionBody();
          return this.markLocation(new Getter(key, body), startLocation);
        } else if ("set".equals(name) && !this.match(TokenType.COLON)) {
          key = this.parseObjectPropertyKey();
          this.expect(TokenType.LPAREN);
          token = this.lookahead;
          if (token.type != TokenType.IDENTIFIER) {
            this.expect(TokenType.RPAREN);
            throw this.createErrorWithToken(startLocation, UNEXPECTED_TOKEN, token.type.toString());
          } else {
            Identifier param = this.parseVariableIdentifier();
            this.expect(TokenType.RPAREN);
            FunctionBody body = this.parseFunctionBody();
            if ((this.strict || body.isStrict()) && Utils.isRestrictedWord(param.name)) {
              throw this.createError(STRICT_PARAM_NAME);
            }
            return this.markLocation(new Setter(key, param, body), startLocation);
          }
        }
      }

      this.expect(TokenType.COLON);
      Expression value = this.parseAssignmentExpression();
      return this.markLocation(new DataProperty(key, value), startLocation);
    }
    if (this.eof() || token.type.klass == TokenClass.Punctuator) {
      throw this.createUnexpected(token);
    } else {
      PropertyName key = this.parseObjectPropertyKey();
      this.expect(TokenType.COLON);
      Expression value = this.parseAssignmentExpression();
      return this.markLocation(new DataProperty(key, value), startLocation);
    }
  }

  private static class ObjectPropertyCombination {
    public static final ObjectPropertyCombination NIL = new ObjectPropertyCombination(false, false, false);
    public final boolean hasInit;
    public final boolean hasGetter;
    public final boolean hasSetter;

    private ObjectPropertyCombination(boolean hasInit, boolean hasGetter, boolean hasSetter) {
      this.hasInit = hasInit;
      this.hasGetter = hasGetter;
      this.hasSetter = hasSetter;
    }

    public ObjectPropertyCombination withInit() {
      return new ObjectPropertyCombination(true, this.hasGetter, this.hasSetter);
    }

    public ObjectPropertyCombination withGetter() {
      return new ObjectPropertyCombination(this.hasInit, true, this.hasSetter);
    }

    public ObjectPropertyCombination withSetter() {
      return new ObjectPropertyCombination(this.hasInit, this.hasGetter, true);
    }
  }

  private static class ParamsInfo {
    @NotNull
    final ArrayList<Identifier> params = new ArrayList<>();
    @Nullable
    SourceLocation stricted;
    @Nullable
    SourceLocation firstRestricted;
    @Nullable
    String message;
  }

  private static class ExprStackItem {
    final SourceLocation startLocation;
    @NotNull
    final Expression left;
    @NotNull
    final BinaryOperator operator;
    final int precedence;

    ExprStackItem(@NotNull SourceLocation startLocation, @NotNull Expression left, @NotNull BinaryOperator operator) {
      this.startLocation = startLocation;
      this.left = left;
      this.operator = operator;
      this.precedence = operator.getPrecedence().ordinal();
    }
  }
}
