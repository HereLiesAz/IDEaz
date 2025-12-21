import React from 'react';
import { View, Text, StyleSheet } from 'react-native';

const App = () => {
  return React.createElement(View, { style: styles.container },
    React.createElement(Text, { style: styles.text }, "Hello React Native!")
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5FCFF',
  },
  text: {
    fontSize: 20,
    textAlign: 'center',
    margin: 10,
  },
});

export default App;
