import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:driv_acc_serial/new_user_page.dart';

void main() {
  runApp(const MyApp());
}

String serverAddress = "";

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const MyHomePage(title: 'Driving Access'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  static const platform = MethodChannel('com.driving_access/backend');

  String matchResult = '';
  bool scan = false;

  Future<void> matchFingerprint(BuildContext context) async {
    try {
      Uint8List imageData = await platform.invokeMethod('captureFingerprint');

      String result = await platform.invokeMethod('matchFingerprint', {
        "ImageData": imageData,
      });
      print(result);
      setState(() {
        matchResult = "Match found\nAccess Granted";
      });
    } on PlatformException catch (e) {
      debugPrint("Error: ${e.message}");
      setState(() {
        matchResult = "${e.message}\nTry Again";
      });
    }
  }

  void _showSettingsDialog() {
    TextEditingController serverAddressController =
        TextEditingController(text: serverAddress);

    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: Text('Settings'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: serverAddressController,
                decoration: InputDecoration(
                  labelText: 'Server Address',
                  border: OutlineInputBorder(),
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.of(context).pop();
              },
              child: Text('Cancel'),
            ),
            TextButton(
              onPressed: () {
                setState(() {
                  serverAddress = serverAddressController.text;
                });
                Navigator.of(context).pop();
              },
              child: Text('Save'),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
        appBar: AppBar(
          backgroundColor: Theme.of(context).colorScheme.inversePrimary,
          title: Text(widget.title),
          actions: [
            IconButton(
                onPressed: () => {
                      setState(() {
                        matchResult = '';
                      })
                    },
                icon: Icon(Icons.refresh)),
            IconButton(
              icon: Icon(Icons.settings),
              onPressed: _showSettingsDialog,
            ),
          ],
        ),
        body: Column(
          children: <Widget>[
            SizedBox(
              height: 50,
            ),
            Align(
              alignment: Alignment.topCenter,
              child: ElevatedButton(
                onPressed: () {
                  setState(() {
                    scan = true;
                  });
                  matchFingerprint(context);
                },
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.lightGreen,
                ),
                child: const Text('Start',
                    style: TextStyle(color: Colors.black, fontSize: 20)),
              ),
            ),
            Expanded(
              child: Center(
                child: Column(children: [
                  scan ? Text("SCAN") : Container(),
                  matchResult != '' ? Text(matchResult) : Container(),
                ]),
              ),
            ),
            Align(
              alignment: Alignment.bottomCenter,
              child: ElevatedButton(
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(builder: (context) => NewUserPage()),
                  );
                },
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.blue[300],
                ),
                child: const Text('New User'),
              ),
            ),
            SizedBox(
              height: 20,
            ),
          ],
        ));
  }
}
