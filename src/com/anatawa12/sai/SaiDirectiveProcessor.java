package com.anatawa12.sai;

public class SaiDirectiveProcessor {
    public SaiDirectiveProcessor(Parser parser, TokenStream ts) {
        this.ts = ts;
        this.parser = parser;
    }

    public void processDirective(String directive, int directiveStartPosition) {
        reset(directive, directiveStartPosition);
        String name = readToken();
        if (name == null) return;
        switch (name) {
            case "line":
                processLineDirective();
                break;
            default:
                parser.addWarning("msg.sai.directive.unknown.directive", name,
                        directiveStartPosition + position, name.length());
                break;
        }
    }

    private void processLineDirective() {
        Integer line = readInteger();
        if (line == null) return;
        String name = readString();
        if (name == null) return;
        int lineno = ts.lineno;
        parser.addLineNumberMapping(lineno + 1, line + 1, name);
    }

    private Integer readInteger() {
        String token = readToken();
        try {
            if (token != null)
                return Integer.parseInt(token);
        } catch (NumberFormatException ignored) {
        }
        parser.addError("msg.sai.directive.unexpected.token", "integer",
                directiveStartPosition + position, token == null ? 1 : token.length());
        return null;
    }

    private String readToken() {
        skipSpace();
        builder.setLength(0);
        int c;
        while (!TokenStream.isJSSpace(c = cur()) && c != -1) {
            builder.append((char)c);
            position++;
        }
        if (builder.length() == 0)
            return null;
        return builder.toString();
    }

    public String readString() {
        skipSpace();
        int c;
        if (cur() != '"') {
            parser.addError("msg.sai.directive.unexpected.char", "'\"'",
                    directiveStartPosition + position, 1);
            return null;
        }

        builder.setLength(0);

        while (true) {
            position++;
            c = cur();
            if (c == '\"') {
                break;
            } else if (c == '\\') {
                position++;
                c = cur();
                switch (c) {
                    case 'b': c = '\b'; break;
                    case 'f': c = '\f'; break;
                    case 'n': c = '\n'; break;
                    case 'r': c = '\r'; break;
                    case 't': c = '\t'; break;
                    case 'v': c = 0xb; break;
                    case 'u': {
                        int theC = 0;
                        for (int i = 0; i < 4; i++) {
                            position++;
                            c = cur();
                            theC <<= 4;
                            switch (c) {
                                case '0':
                                case '1':
                                case '2':
                                case '3':
                                case '4':
                                case '5':
                                case '6':
                                case '7':
                                case '8':
                                case '9':
                                    theC += (c - '0');
                                    break;
                                case 'a':
                                case 'b':
                                case 'c':
                                case 'd':
                                case 'e':
                                case 'f':
                                    theC += (c - 'a') + 10;
                                    break;
                                case 'A':
                                case 'B':
                                case 'C':
                                case 'D':
                                case 'E':
                                case 'F':
                                    theC += (c - 'A') + 10;
                                    break;
                                default: {
                                    parser.addError("msg.sai.directive.unexpected.eol",
                                            directiveStartPosition + position, 1);
                                    return null;
                                }
                            }
                        }
                        c = theC;
                        break;
                    }
                    case -1: {
                        parser.addError("msg.sai.directive.unexpected.eol",
                                directiveStartPosition + position, 1);
                        return null;
                    }
                }
            } else if (c == -1) {
                parser.addError("msg.sai.directive.unexpected.char", "'\"'",
                        directiveStartPosition + position, 1);
                return null;
            }
            builder.append((char) c);
        }
        return builder.toString();
    }

    private void skipSpace() {
        while (TokenStream.isJSSpace(cur()))
            position++;
    }

    private int cur() {
        if (position >= directive.length())
            return -1;
        return directive.charAt(position);
    }

    public void reset(String directive, int directiveStartPosition) {
        this.directive = directive;
        this.directiveStartPosition = directiveStartPosition;
        this.position = 0;
    }

    private final TokenStream ts;
    private final Parser parser;
    private final StringBuilder builder = new StringBuilder();
    private String directive;
    private int position;
    private int directiveStartPosition;
}
