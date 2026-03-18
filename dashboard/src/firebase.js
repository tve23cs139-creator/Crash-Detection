import { initializeApp } from 'firebase/app';
import { getDatabase } from 'firebase/database';

// Firebase Configuration
// Replace these values with your Firebase project credentials
const firebaseConfig = {
  apiKey: "AIzaSyBk1jg2jDu1B7Fa1eL-0KXW5rK9AVJmJ5Y",
  authDomain: "crash-detection-6785b.firebaseapp.com",
  databaseURL: "https://crash-detection-6785b-default-rtdb.asia-southeast1.firebasedatabase.app", // PASTE IT HERE
  projectId: "crash-detection-6785b",
  storageBucket: "crash-detection-6785b.firebasestorage.app",
  messagingSenderId: "809569899144",
  appId: "1:809569899144:web:f0381f7af19fd723c2c53d",
  measurementId: "G-401FS1EW3M"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);

// Get a reference to the database service
export const database = getDatabase(app);
