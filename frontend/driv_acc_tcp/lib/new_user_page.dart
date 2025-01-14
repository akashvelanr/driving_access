import 'dart:convert';

import 'package:driv_acc_tcp/main.dart';
import 'package:http/http.dart' as http;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class NewUserPage extends StatefulWidget {
  NewUserPage({Key? key}) : super(key: key);

  @override
  _NewUserPageState createState() => _NewUserPageState();
}

class _NewUserPageState extends State<NewUserPage> {
  static const platform = MethodChannel('com.driving_access/backend');
  String matchResult = '';

  Future<Map<String, dynamic>> matchFpServer(
      Uint8List templateBytes, String apiUrl) async {
    try {
      // Encode the byte array to Base64
      String templateString = base64Encode(templateBytes);

      // Send the POST request
      final response = await http.post(
        Uri.parse(apiUrl),
        headers: {
          "Content-Type": "application/json",
        },
        body: templateString,
      );
      print(response.body);
      // Check the response status
      if (response.statusCode == 200 || response.statusCode == 456) {
        return jsonDecode(response.body);
      } else {
        print('Error: ${response.statusCode} - ${response.reasonPhrase}');
        return jsonDecode(response.body);
      }
    } catch (e) {
      throw Exception('Error while matching fingerprint: $e');
    }
  }

  Future<void> matchFingerprint(BuildContext context) async {
    try {
      Uint8List imageData = await platform
          .invokeMethod('captureFingerprint', {'ServerIP': fpTcpIp});

      Map<String, dynamic> result = await matchFpServer(
          imageData, "http://$serverAddress/fingerprint/match");

      setState(() {
        print("API Response:");
        if (result['error'] != null) {
          print("Error: ${result['error']}");
          matchResult = "${result['error']}\nTry Again";
        } else {
          print("Name: ${result['name']}");
          print("Similarity: ${result['similarity']}");
          print("Eligibility: ${result['eligibility']}");
          print("Match Template: ${result['matchTempleteString']}");

          if (result['eligibility'] == 1) {
            matchResult = "Name: ${result['name']}\nEligible\nUser Registered";
            loadFingerprintTemplate(result['matchTempleteString']);
          } else {
            matchResult = "Name: ${result['name']}\nNot Eligible";
          }
        }
      });
    } on PlatformException catch (e) {
      setState(() {
        matchResult = "${e.message}\nTry Again";
      });
    } catch (e) {
      setState(() {
        matchResult = "Unexpected Error: $e";
      });
    }
  }

  Future<void> loadFingerprintTemplate(String base64Template) async {
    try {
      final String result = await platform.invokeMethod(
        'loadFingerprintTemplate',
        {'base64Template': base64Template},
      );

      print(result);
    } on PlatformException catch (e) {
      print(e.message);
    } catch (e) {
      print(e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('New User'),
        backgroundColor: Colors.blue[400],
      ),
      body: Column(
        children: <Widget>[
          SizedBox(height: 50),
          Align(
            alignment: Alignment.topCenter,
            child: ElevatedButton(
              onPressed: () async {
                setState(() {
                  matchResult = '';
                });
                await matchFingerprint(context);
              },
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.lightGreen,
              ),
              child: const Text('Scan',
                  style: TextStyle(color: Colors.black, fontSize: 20)),
            ),
          ),
          SizedBox(
            height: 40,
          ),
          Expanded(
            child: matchResult.isNotEmpty
                ? Column(
                    children: <Widget>[
                      Text(
                        'Match Result:',
                        style: TextStyle(
                          fontSize: 18,
                        ),
                      ),
                      Text(
                        matchResult,
                        style: TextStyle(
                          fontSize: 20,
                        ),
                      ),
                    ],
                  )
                : Container(),
          ),
          if (matchResult.isNotEmpty)
            Align(
              alignment: Alignment.bottomCenter,
              child: ElevatedButton(
                onPressed: () {
                  Navigator.pop(context);
                },
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.blue[300],
                ),
                child: const Text('Back'),
              ),
            ),
          SizedBox(height: 20),
        ],
      ),
    );
  }
}
