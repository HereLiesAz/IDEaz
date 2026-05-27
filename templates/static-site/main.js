const button = document.getElementById("counter");
let count = 0;
button.addEventListener("click", () => {
    count += 1;
    button.textContent = `Clicked ${count} ${count === 1 ? "time" : "times"}`;
});
