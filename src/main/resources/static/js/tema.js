// Alterna entre tema claro y oscuro y lo recuerda en este navegador.
(function () {
    var boton = document.getElementById('boton-tema');
    if (!boton) { return; }
    boton.addEventListener('click', function () {
        var actual = document.documentElement.getAttribute('data-bs-theme') === 'dark' ? 'dark' : 'light';
        var nuevo = actual === 'dark' ? 'light' : 'dark';
        document.documentElement.setAttribute('data-bs-theme', nuevo);
        try { localStorage.setItem('pic2filament-tema', nuevo); } catch (e) { }
    });
})();
