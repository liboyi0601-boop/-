package ScheduleAgorithm;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonSupport
{
	private JsonSupport()
	{
	}

	public static void writeJson(Path path, Object value) throws IOException
	{
		Path parent = path.getParent();
		if(parent != null)
		{
			Files.createDirectories(parent);
		}

		try(BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8))
		{
			writer.write(toJson(value));
			writer.newLine();
		}
	}

	public static BufferedWriter newJsonlWriter(Path path) throws IOException
	{
		Path parent = path.getParent();
		if(parent != null)
		{
			Files.createDirectories(parent);
		}
		return Files.newBufferedWriter(path, StandardCharsets.UTF_8);
	}

	public static void appendJsonLine(Writer writer, Object value) throws IOException
	{
		writer.write(toJson(value));
		writer.write('\n');
		writer.flush();
	}

	public static String readString(Path path) throws IOException
	{
		StringBuilder builder = new StringBuilder();
		try(Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
		{
			char[] buffer = new char[4096];
			int count;
			while((count = reader.read(buffer)) != -1)
			{
				builder.append(buffer, 0, count);
			}
		}
		return builder.toString();
	}

	public static Object parseJson(String json)
	{
		return new Parser(json).parse();
	}

	public static String toJson(Object value)
	{
		StringBuilder builder = new StringBuilder();
		appendJson(builder, value);
		return builder.toString();
	}

	@SuppressWarnings("unchecked")
	private static void appendJson(StringBuilder builder, Object value)
	{
		if(value == null)
		{
			builder.append("null");
		}
		else if(value instanceof String)
		{
			appendQuotedString(builder, (String)value);
		}
		else if(value instanceof Number || value instanceof Boolean)
		{
			builder.append(String.valueOf(value));
		}
		else if(value instanceof Map<?, ?>)
		{
			builder.append('{');
			Iterator<Map.Entry<Object, Object>> iterator =
					((Map<Object, Object>)value).entrySet().iterator();
			while(iterator.hasNext())
			{
				Map.Entry<Object, Object> entry = iterator.next();
				appendQuotedString(builder, String.valueOf(entry.getKey()));
				builder.append(':');
				appendJson(builder, entry.getValue());
				if(iterator.hasNext())
				{
					builder.append(',');
				}
			}
			builder.append('}');
		}
		else if(value instanceof List<?>)
		{
			builder.append('[');
			Iterator<Object> iterator = ((List<Object>)value).iterator();
			while(iterator.hasNext())
			{
				appendJson(builder, iterator.next());
				if(iterator.hasNext())
				{
					builder.append(',');
				}
			}
			builder.append(']');
		}
		else
		{
			appendQuotedString(builder, String.valueOf(value));
		}
	}

	private static void appendQuotedString(StringBuilder builder, String value)
	{
		builder.append('"');
		for(int index = 0; index < value.length(); index++)
		{
			char current = value.charAt(index);
			switch(current)
			{
				case '\\':
					builder.append("\\\\");
					break;
				case '"':
					builder.append("\\\"");
					break;
				case '\n':
					builder.append("\\n");
					break;
				case '\r':
					builder.append("\\r");
					break;
				case '\t':
					builder.append("\\t");
					break;
				default:
					if(current < 0x20)
					{
						builder.append(String.format("\\u%04x", (int)current));
					}
					else
					{
						builder.append(current);
					}
			}
		}
		builder.append('"');
	}

	private static final class Parser
	{
		private final String input;
		private int index;

		private Parser(String input)
		{
			this.input = input;
			this.index = 0;
		}

		private Object parse()
		{
			skipWhitespace();
			Object value = parseValue();
			skipWhitespace();
			if(index != input.length())
			{
				throw new IllegalArgumentException("Unexpected trailing JSON content");
			}
			return value;
		}

		private Object parseValue()
		{
			skipWhitespace();
			if(index >= input.length())
			{
				throw new IllegalArgumentException("Unexpected end of JSON input");
			}

			char current = input.charAt(index);
			switch(current)
			{
				case '{':
					return parseObject();
				case '[':
					return parseArray();
				case '"':
					return parseString();
				case 't':
					return parseLiteral("true", Boolean.TRUE);
				case 'f':
					return parseLiteral("false", Boolean.FALSE);
				case 'n':
					return parseLiteral("null", null);
				default:
					if(current == '-' || Character.isDigit(current))
					{
						return parseNumber();
					}
					throw new IllegalArgumentException("Unexpected character in JSON: " + current);
			}
		}

		private Map<String, Object> parseObject()
		{
			Map<String, Object> map = new LinkedHashMap<String, Object>();
			index++;
			skipWhitespace();
			if(peek('}'))
			{
				index++;
				return map;
			}

			while(true)
			{
				String key = parseString();
				skipWhitespace();
				expect(':');
				Object value = parseValue();
				map.put(key, value);
				skipWhitespace();
				if(peek('}'))
				{
					index++;
					return map;
				}
				expect(',');
			}
		}

		private List<Object> parseArray()
		{
			List<Object> list = new ArrayList<Object>();
			index++;
			skipWhitespace();
			if(peek(']'))
			{
				index++;
				return list;
			}

			while(true)
			{
				list.add(parseValue());
				skipWhitespace();
				if(peek(']'))
				{
					index++;
					return list;
				}
				expect(',');
			}
		}

		private String parseString()
		{
			expect('"');
			StringBuilder builder = new StringBuilder();
			while(index < input.length())
			{
				char current = input.charAt(index++);
				if(current == '"')
				{
					return builder.toString();
				}
				if(current == '\\')
				{
					if(index >= input.length())
					{
						throw new IllegalArgumentException("Unexpected end of escaped JSON string");
					}
					char escaped = input.charAt(index++);
					switch(escaped)
					{
						case '"':
						case '\\':
						case '/':
							builder.append(escaped);
							break;
						case 'b':
							builder.append('\b');
							break;
						case 'f':
							builder.append('\f');
							break;
						case 'n':
							builder.append('\n');
							break;
						case 'r':
							builder.append('\r');
							break;
						case 't':
							builder.append('\t');
							break;
						case 'u':
							builder.append((char)Integer.parseInt(input.substring(index, index + 4), 16));
							index += 4;
							break;
						default:
							throw new IllegalArgumentException("Invalid JSON escape sequence: \\" + escaped);
					}
				}
				else
				{
					builder.append(current);
				}
			}
			throw new IllegalArgumentException("Unterminated JSON string");
		}

		private Object parseNumber()
		{
			int start = index;
			if(input.charAt(index) == '-')
			{
				index++;
			}
			while(index < input.length() && Character.isDigit(input.charAt(index)))
			{
				index++;
			}
			if(index < input.length() && input.charAt(index) == '.')
			{
				index++;
				while(index < input.length() && Character.isDigit(input.charAt(index)))
				{
					index++;
				}
			}
			if(index < input.length() && (input.charAt(index) == 'e' || input.charAt(index) == 'E'))
			{
				index++;
				if(index < input.length() && (input.charAt(index) == '+' || input.charAt(index) == '-'))
				{
					index++;
				}
				while(index < input.length() && Character.isDigit(input.charAt(index)))
				{
					index++;
				}
			}

			String number = input.substring(start, index);
			if(number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0)
			{
				return Double.valueOf(number);
			}

			long longValue = Long.parseLong(number);
			if(longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE)
			{
				return Integer.valueOf((int)longValue);
			}
			return Long.valueOf(longValue);
		}

		private Object parseLiteral(String literal, Object value)
		{
			if(input.regionMatches(index, literal, 0, literal.length()))
			{
				index += literal.length();
				return value;
			}
			throw new IllegalArgumentException("Invalid JSON literal: " + literal);
		}

		private void expect(char expected)
		{
			skipWhitespace();
			if(index >= input.length() || input.charAt(index) != expected)
			{
				throw new IllegalArgumentException("Expected JSON character: " + expected);
			}
			index++;
		}

		private boolean peek(char expected)
		{
			return index < input.length() && input.charAt(index) == expected;
		}

		private void skipWhitespace()
		{
			while(index < input.length())
			{
				char current = input.charAt(index);
				if(current == ' ' || current == '\n' || current == '\r' || current == '\t')
				{
					index++;
				}
				else
				{
					break;
				}
			}
		}
	}
}
