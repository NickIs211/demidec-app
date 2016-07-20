package Database.DBCreator;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class DBCreator {

    public static void addFilesToDatabase(File[] files) {
        //File file1 = new File("All Focused Quizzes.bin");
        //File file = new File("temp.xml");
        for(File file : files) {
            DefaultHandler handler = new DefaultHandler(){
                private List<String> brokenSQL;
                private SQLCommand sqlCommand;
                private ArrayList<String> tables = new ArrayList<String>();
                private Stack<Node> order = new Stack<Node>();
                private Node root;
                private Connection connection = null;
                private boolean first = true;
                private int valuesQueued = 0;
                private int maxValueQueue = 1000;
                private Statement stmnt;
                private int acceptedParams;
                @Override
                public void startDocument(){
                    try {
                        System.out.println("Connecting to db...");
                        connection = DriverManager.getConnection("jdbc:sqlite:database.db");
                        System.out.println("Connected to db!");
                        DatabaseMetaData metaData = connection.getMetaData();
                        stmnt = connection.createStatement();
                        ResultSet rs = metaData.getTables(null, null, null, new String[]{"TABLE"});
                        while(rs.next()){
                            tables.add(rs.getString("TABLE_NAME"));
                        }
                        brokenSQL = new ArrayList<>();
                    } catch (SQLException e) {
                        e.printStackTrace();
                        return;
                    }
                }

                @Override
                public void startElement(String uri, String localName, String qName, org.xml.sax.Attributes attributes) throws SAXException {
                    if(first) {
                        root = new Node(qName);
                        first = false;
                        order.add(root);
                        return;
                    }
                    Node newNode = new Node(qName);
                    order.peek().addChild(newNode);
                    order.add(newNode);


                }

                @Override
                public void endElement(String uri, String localName, String qName) {
                    order.pop();
                    if(!order.empty() && order.peek() == root){
                        Node workingNode = root.getChildren().get(0);
                        if(!tables.contains(workingNode.getName())) {
                            String sql = "CREATE TABLE " + workingNode.getName() + "\n(\n";
                            for (int i = 0; i < workingNode.getChildren().size(); i++) {
                                Node child = workingNode.getChildren().get(i);
                                sql += child.getName() + " " + child.dataType();
                                if (i + 1 <= workingNode.getChildren().size()) {
                                    sql += ",\n";
                                }
                            }
                            sql += "PRIMARY KEY (" + workingNode.getChildren().get(0).getName() +")";
                            sql += "\n);";
                            tables.add(qName);
                            try {
                                System.out.println("CREATING TABLE: " + qName);
                                connection.createStatement().executeUpdate(sql);
                            } catch (SQLException e) {
                                e.printStackTrace();
                                System.out.println(sql);
                            }
                            if(sqlCommand != null){
                                addSQLToBatch(sqlCommand.command);
                                valuesQueued = 0;
                            }

                        }
                        if(sqlCommand == null){
                            createSqlStatement(workingNode);
                        }
                        if(!sqlCommand.table.equals(workingNode.getName())){
                            addSQLToBatch(sqlCommand.command);
                            valuesQueued = 0;
                            createSqlStatement(workingNode);
                        }
                        if(workingNode.getChildren().size() != acceptedParams){
                            //System.out.println(workingNode.getChildren().get(0).data);
                            String data = "";
                            for(int i = 0; i < workingNode.getChildren().size(); i++){
                                Node child = workingNode.getChildren().get(i);
                                child.data = child.data.replace("\'", "\'\'");
                                data += "'" + child.data + "'";
                                if(i + 1 < workingNode.getChildren().size()) {
                                    data += ", ";
                                }
                            }
                            data = data.trim();
                            if(data.charAt(data.length() - 1) == ','){
                                data = data.substring(0, data.length() - 1);
                            }
                            String sql = "INSERT OR IGNORE INTO " + workingNode.getName() + " (";
                            String column = "";
                            for(int i = 0; i < workingNode.getChildren().size(); i++){
                                Node child = workingNode.getChildren().get(i);
                                column += "'" + child.getName() + "'";
                                if(i + 1 < workingNode.getChildren().size()) {
                                    column += ", ";
                                }
                            }
                            column = column.trim();
                            if(column.charAt(column.length() - 1) == ','){
                                column = column.substring(0, column.length() - 1);
                            }
                            sql +=column + ")\nVALUES" + "(" + data + ")";
                            brokenSQL.add(sql);
                        }
                        if(workingNode.getChildren().size() == acceptedParams){
                            String data = "";
                            for(int i = 0; i < workingNode.getChildren().size(); i++){
                                Node child = workingNode.getChildren().get(i);
                                child.data = child.data.replace("\'", "\'\'");
                                data += "'" + child.data + "'";
                                if(i + 1 < workingNode.getChildren().size()) {
                                    data += ", ";
                                }
                            }
                            data = data.trim();
                            if(data.charAt(data.length() - 1) == ','){
                                data = data.substring(0, data.length() - 1);
                            }
                            if(valuesQueued < maxValueQueue){
                                sqlCommand.command += "(" + data + "), ";
                                valuesQueued++;
                            }else{
                                addSQLToBatch(sqlCommand.command);
                                createSqlStatement(workingNode);
                                sqlCommand.command += "(" + data + "),  ";
                                valuesQueued = 1;
                            }
                        }
                        order.pop();
                        root = new Node(root.getName());
                        order.add(root);
                    }

                /*if(order.peek() != root)
                    order.pop();
                if(order.peek() == root) {
                    for (Node<String> parent : root.getChildren()) {
                        for(Node<String> child : parent.getChildren()){
                            System.out.println(child.getName() + ": " + child.getData());
                        }
                    }
                }*/
                /* if(order.peek() == root){
                    for(Node<String> parent : root.getChildren()){

                    }
                    /*for(Node<String> parent : root.getChildren()){
                        String sql = "CREATE TABLE " + parent.getName() + "(";
                        String primaryKey = "PRIMARY KEY(" + parent.getData() + ")" + System.getProperty("line.separator") + ");";
                        for(Node<String> child : parent.getChildren()){
                            sql += child.getData() + " ";
                            try{
                                int temp = Integer.parseInt(child.getData());
                                sql+= "INT, " + System.getProperty("line.separator");
                            }catch(NumberFormatException e){
                                sql += "NVARCHAR, " + System.getProperty("line.separator");
                            }
                        }
                        sql +=  primaryKey;

                        try {
                            stmnt.executeUpdate(sql);
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                        root = new Node<String>("root");
                        order.pop();
                        order.add(root);
                    }*/

                }

                private void addSQLToBatch(String sqlStmnt) {
                    sqlStmnt = sqlStmnt.trim();
                    if(sqlStmnt.charAt(sqlStmnt.length() - 1) == ',')
                        sqlStmnt = sqlStmnt.substring(0, sqlStmnt.length() - 1) + ";";
                    try {
                        stmnt.addBatch(sqlStmnt);
                        valuesQueued = 0;
                    } catch (SQLException e) {
                        e.printStackTrace();
                        System.exit(0);
                    /*int begin =  sqlStmnt.substring(0, sqlStmnt.indexOf("VALUES") + 6).length();
                    String sql = sqlStmnt.substring(0, sqlStmnt.indexOf("VALUES") + 6);
                    while(begin < sqlStmnt.length()){
                        int parLoc = sqlStmnt.indexOf('(', begin);
                        String values = sqlStmnt.substring(parLoc, sqlStmnt.indexOf(')', parLoc) + 1);
                        String command = sql + values;
                        try {
                            stmnt.addBatch(command);
                            stmnt.executeBatch();
                            stmnt.clearBatch();
                        } catch (SQLException e1) {
                            e1.printStackTrace();
                            System.out.println(command);
                            System.exit(0);
                        }
                        begin += values.length();
                    }*/


                    }
                }

                private void createSqlStatement(Node workingNode) {
                    acceptedParams = workingNode.getChildren().size();
                    sqlCommand = new SQLCommand("INSERT OR IGNORE INTO " + workingNode.getName() + " (", workingNode.getName());
                    String column = "";
                    for(int i = 0; i < workingNode.getChildren().size(); i++){
                        Node child = workingNode.getChildren().get(i);
                        column += "'" + child.getName() + "'";
                        if(i + 1 < workingNode.getChildren().size()) {
                            column += ", ";
                        }
                    }
                    column = column.trim();
                    if(column.charAt(column.length() - 1) == ','){
                        column = column.substring(0, column.length() - 1);
                    }
                    sqlCommand.command +=column + ")\nVALUES";
                }

                @Override
                public void characters(char[] ch, int start, int length) {
                    for (int i = start; i < start + length; i++) {
                        order.peek().data += ch[i];
                    }
                    order.peek().data = order.peek().data.trim();
                }

                @Override
                public void endDocument(){
                    try {
                        if(valuesQueued != 0){
                            addSQLToBatch(sqlCommand.command);
                        }
                        for(String sql : brokenSQL){
                            stmnt.addBatch(sql);
                        }
                        System.out.println("EXEC BATCH");
                        stmnt.executeBatch();
                        System.out.println("BATCH DONE");
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    try {
                        stmnt.close();
                        connection.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            };

            byte[] data = new byte[(int) file.length()];
            try {
                DataInputStream in = new DataInputStream(new FileInputStream((file)));
                in.readFully(data);
                File output = new File("temp.xml");
                DataOutputStream out = new DataOutputStream(new FileOutputStream(output));
                out.write(data);
                out.close();
                in.close();
                FileReader fr = new FileReader(output);
                BufferedReader br = new BufferedReader(fr);
                String line = br.readLine().replace("1.0", "1.1");
                br.close();
                fr.close();
                RandomAccessFile rFile = new RandomAccessFile(output, "rws");
                rFile.write(line.getBytes());
                rFile.close();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }


            try {
                File temp = new File("temp.xml");
                SAXParserFactory factory = SAXParserFactory.newInstance();
                SAXParser parser = factory.newSAXParser();
                parser.parse(temp, handler);
                Files.delete(temp.toPath());
            } catch (ParserConfigurationException | SAXException | IOException e) {
                e.printStackTrace();
            }
        }
    }
}