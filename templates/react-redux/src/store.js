import { configureStore, createSlice } from "@reduxjs/toolkit";

const counter = createSlice({
  name: "counter",
  initialState: { value: 0 },
  reducers: {
    inc: (s) => { s.value += 1; },
    dec: (s) => { s.value -= 1; },
    reset: (s) => { s.value = 0; },
  },
});

export const { inc, dec, reset } = counter.actions;

export const store = configureStore({
  reducer: { counter: counter.reducer },
});
