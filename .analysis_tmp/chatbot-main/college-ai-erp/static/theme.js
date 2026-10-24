function toggleTheme(){

    document.body.classList.toggle(
        "light-mode"
    );

    localStorage.setItem(
        "theme",
        document.body.classList.contains(
            "light-mode"
        )
    );
}

if(
    localStorage.getItem("theme")
    === "true"
){

    document.body.classList.add(
        "light-mode"
    );
}